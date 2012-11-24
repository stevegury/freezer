package com.github.stevegury.freezer

import com.amazonaws.auth.PropertiesCredentials
import com.amazonaws.services.glacier.TreeHashGenerator.calculateTreeHash
import com.codahale.jerkson.Json.parse
import com.github.stevegury.freezer.Util.relativize

import java.io.{File, FileInputStream}
import util.matching.Regex


object Freezer {

  def parseArgs(args: Array[String]) = {
    // command: backup: backup the current dir
    //          check:  check the current dir with the list of files in the last inventory
    //                  if no inventory is available, request the creation of one
    //          restore <name>: restore the all the files in a vault
    //                  if no archives are available, request one
    // options: ???
  }

  def help() {
    println("Usage: freezer <action>")
    println("  actions are: backup")
    println("   - backup: backup the current (and all subdirectories)")
    println("   - restore vaultName: restore the vaultName into the current directory")
    println("   - list: List the current states of the server")
  }

  def main(args: Array[String]) {
    val action = args.headOption getOrElse "help"

    val dir = new File(".").getAbsoluteFile.getParentFile
    val cfg = readConfig(dir) getOrElse { createConfig(dir) }
    val client = {
      val credentials = new PropertiesCredentials(new File(cfg.credentials))
      new GlacierClient(credentials, cfg.endpoint)
    }
    // TODO: Handle vault name collision

    action match {
      case "backup" => backup(dir, cfg, client)
      case "restore" if args.size > 1 => restore(dir, args(1), client)
      case "list" => list(cfg, client)
      case _ => help()
    }
  }

  def readConfig(dir: File): Option[Config] = {
    val cfgFile = new File(dir, configFilename)
    if (dir == null) None
    else if (!cfgFile.exists()) readConfig(dir.getParentFile)
    else {
      val buffer = new Array[Byte](cfgFile.length().toInt)
      new FileInputStream(cfgFile).read(buffer)
      val json = new String(buffer)
      Some(parse[Config](json))
    }
  }

  def createConfig(dir: File): Config = {
    val vaultName = dir.getName
    val path = dir.getAbsolutePath + File.separator + configFilename
    val config = Config(path, vaultName)
    config.save()
    config
  }

  def backup(dir: File, cfg: Config, client: GlacierClient) = {
    val vault = getOrCreateVault(client, cfg.vaultName)
    val root = new File(cfg.path).getParentFile
    val index = cfg.archiveInfos map { info => info.desc -> info } toMap

    val updated = updatedFiles(dir, index, relativize(root, _), Vector.empty[File])

    val newIndex = updated.foldLeft(index) { (index, file) =>
      val path = relativize(root, file)
      val hash = calculateTreeHash(file)
      val info = vault.upload(file, hash, path)
      index.get(path) foreach { info =>
        vault.deleteArchive(info.archiveId)
      }
      val nextIndex = index + (path -> info)
      cfg.copy(archiveInfos = nextIndex.values.toList).save()
      nextIndex
    }
    cfg.copy(archiveInfos = newIndex.values.toList)
  }

  def list(cfg: Config, client: GlacierClient) = {
    val vault = getOrCreateVault(client, cfg.vaultName)
    vault.getInventory match {
      case Right(archiveInfos) => archiveInfos foreach { info => println(info.desc) }
      case Left(jobId) => println("Inventory in prgress (JobID: '%s')".format(jobId))
    }
  }

  def restore(root: File, vaultName: String, client: GlacierClient) = {
    client.getVault(vaultName) match {
      case Some(vault) => vault.getInventory match {
        case Right(archiveInfos) =>
          vault.download(root, archiveInfos)
        case Left(jobId) =>
          println("Restore requested but not yet ready")
          println("(please redo this command later)")
      }
      case _ => println("Can't find vault '%s'".format(vaultName))
    }
  }

  def getOrCreateVault(client: GlacierClient, vaultName: String) =
    client.getVault(vaultName) getOrElse { client.createVault(vaultName) }

  def updatedFiles(
    file: File, index: Map[String, ArchiveInfo], relativize: File => String, updated: Seq[File]
  ): Seq[File] = {
    if (file.isDirectory)
      file.listFiles().foldLeft(updated) {
        (updated, file) => updatedFiles(file, index, relativize, updated) }
    else if (file.getName == configFilename) { /* skip */ updated }
    else if (file.length() == 0) { /* no supported WTF ??? */ updated }
    else {
      val path = relativize(file)
      val h = calculateTreeHash(file)

      index.get(path) match {
        case Some(info) if h == info.hash => updated
        case _ => updated :+ file
      }
    }
  }

  def filterFiles(
    files: Seq[File], exclusions: Seq[String], relativize: File => String
  ): Seq[File] = {
    files filter { file =>
      exclusions forall { excl =>
        val regex = new Regex(excl)
        val path = relativize(file)
        ! regex.findFirstIn(path).isDefined
      }
    }
  }
}
