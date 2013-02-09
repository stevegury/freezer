package com.github.stevegury.freezer

import com.amazonaws.auth.PropertiesCredentials
import com.amazonaws.services.glacier.TreeHashGenerator.calculateTreeHash
import com.codahale.jerkson.Json.parse
import com.github.stevegury.freezer.Util.relativize
import java.io.{File, FileInputStream}

object Freezer {

  def help() {
    println("Usage: freezer <action>")
    println("  actions are:")
    println("   - create: Just create the '.freezer' file.")
    println("   - backup: backup the current (and all subdirectories).")
    println("   - restore <vaultName>: restore the vaultName into the current directory.")
    println("   - list: List the current state of the vault from the server.")
  }

  def main(args: Array[String]) {
    val action = args.headOption getOrElse "help"

    lazy val dir = new File(".").getAbsoluteFile.getParentFile
    lazy val cfg = readConfig(dir) getOrElse { createConfig(dir) }
    lazy val client = {
      val credentials = new PropertiesCredentials(new File(cfg.credentials))
      new GlacierClient(credentials, cfg.endpoint)
    }
    // TODO: Handle vault name collision

    action match {
      case "create" => client.createVault(cfg.vaultName)
      case "backup" => backup(dir, cfg, client)
      case "restore" if args.size > 1 => restore(dir, cfg, args(1), client)
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
    val vault = client.getVault(cfg.vaultName).
      getOrElse { client.createVault(cfg.vaultName) }
    val root = new File(cfg.path).getParentFile
    val index = cfg.archiveInfos map { info => info.desc -> info } toMap

    val updated = Util.updatedFiles(
      dir, index, relativize(root, _))

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
    val vault = client.getVault(cfg.vaultName).
      getOrElse { client.createVault(cfg.vaultName) }
    vault.getInventory match {
      case Right(archiveInfos) =>
        archiveInfos.sortBy(_.desc) foreach { info => println(info.desc) }
      case Left(jobId) =>
        println("Inventory in progress (JobID: '%s')".format(jobId))
    }
  }

  def restore(root: File, cfg: Config, vaultName: String, client: GlacierClient) = {
    val newCfg = client.getVault(vaultName) match {
      case Some(vault) => vault.getInventory match {
        case Right(archiveInfos) =>
          vault.download(root, archiveInfos)
          cfg.copy(archiveInfos = archiveInfos)
        case Left(jobId) =>
          println("Restore requested but not yet ready")
          println("(please redo this command later)")
          cfg
      }
      case _ =>
        println("Can't find vault '%s'".format(vaultName))
        cfg
    }
    newCfg.copy(vaultName = vaultName)
    newCfg.save()
  }
}
