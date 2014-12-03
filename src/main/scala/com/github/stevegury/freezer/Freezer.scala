package com.github.stevegury.freezer

import com.amazonaws.auth.PropertiesCredentials
import com.amazonaws.services.glacier.TreeHashGenerator.calculateTreeHash
import com.github.stevegury.freezer.Util.relativize
import java.io.{FilenameFilter, FileOutputStream, File}
import java.util.Properties

import com.github.stevegury.freezer.tasks.Backup

import scala.io.StdIn

object Freezer {

  def help() = {
    println("Usage: freezer <action>")
    println("  actions are:")
    println("   - init: Just create the '.freezer' file.")
    println("   - backup: backup the current (and all subdirectories).")
    println("   - inventory: List the current state of the vault from the server.")
    println("   - restore <vaultName>: restore the vaultName into the current directory.")
    -1
  }

  def findRoot(dir: File): Option[File] = {
    val cfgDir = new File(dir, configDirname)
    if (dir == null)
      None
    else if (cfgDir.exists() && cfgDir.isDirectory)
      Some(cfgDir.getParentFile.getAbsoluteFile)
    else
      findRoot(dir.getParentFile)
  }

  def readConfig(dir: File): Option[(File, Config)] = {
    findRoot(dir) map { root =>
      val config = new File(new File(root, configDirname), "config")
      (root, Config.load(config))
    }
  }

  def main(args: Array[String]) {
    val action = args.headOption getOrElse "help"
    val dir = new File(".").getAbsoluteFile.getParentFile

    val exitCode = (action, readConfig(dir)) match {
      case ("init", Some(_)) =>
        error(action, "Already a freezer directory!")
      case ("init", None) =>
        init(dir)
      case ("backup", None) =>
        error(action, "Not a freezer directory! Use 'init' command to initialize a freezer directory.")
      case ("backup", Some((root, cfg))) =>
        backup(dir, root, cfg)
      case ("inventory", None) =>
        error(action, "Not a freezer directory! Use 'init' command to initialize a freezer directory.")
      case ("inventory", Some((root, cfg))) =>
        inventory(cfg)
      case ("restore", None) =>
        restore(dir)
      case ("restore", Some((root, cfg))) =>
        error(action, "Already a freezer directory!")
      case _ => help()
    }
    System.exit(exitCode)
  }

  def error(action: String, txt: String) = {
    Console.err.println("freezer %s error: '%s'".format(action, txt))
    -1
  }

  def clientFromConfig(cfg: Config): GlacierClient = {
    val credentials = new PropertiesCredentials(new File(cfg.credentials))
    new GlacierClient(credentials, cfg.endpoint)
  }

  def init(dir: File) = {
    def getOrDefault(txt: String, default: String) = {
      val userInput = StdIn.readLine("\n%s [%s]: ", txt, default)
      if ("" != userInput) userInput else default
    }
    statusDir(dir).mkdirs()

    val cfg = Config(
      // TODO: Handle vault name collision
      vaultName = getOrDefault("VaultName", dir.getName),
      credentials = getOrDefault("Credential location", defaultCredentialsFilename),
      endpoint = getOrDefault("Endpoint", defaultEndpoint),
      exclusions = getOrDefault("Exclusions (comma separated)", ".*\\.DS_Store$").split(",").map(_.r)
    )
    val cfgOutput = new File(configDir(dir), "config")
    cfg.save(cfgOutput)
    0
  }

  def backup(dir: File, root: File, cfg: Config) = {
    val client = clientFromConfig(cfg)
    val vault = client.getVault(cfg.vaultName) getOrElse {
      println("Creating Vault '%s'...".format(cfg.vaultName))
      client.createVault(cfg.vaultName)
    }
    val task = new Backup(dir, root, cfg, vault)
    task.run()

    0
  }

  def inventory(cfg: Config) = {
    val client = clientFromConfig(cfg)
    val vault = client.getVault(cfg.vaultName).
      getOrElse { client.createVault(cfg.vaultName) }
    vault.getInventory match {
      case Right(archiveInfos) =>
        archiveInfos.sortBy(_.desc) foreach { info => println(info.desc) }
      case Left(jobId) =>
        println("Inventory in progress (JobID: '%s')".format(jobId))
    }
    0
  }

  def restore(dir: File): Int = {
//    val client = clientFromConfig(cfg)
//    val vault = client.getVault(cfg.vaultName).get
//    val root = new File(cfg.path).getParentFile
//
//    val newCfg = client.getVault(vaultName) match {
//      case Some(vault) => vault.getInventory match {
//        case Right(archiveInfos) =>
//          vault.download(root, archiveInfos)
//          cfg.copy(archiveInfos = archiveInfos)
//        case Left(jobId) =>
//          println("Restore requested but not yet ready")
//          println("(please redo this command later)")
//          cfg
//      }
//      case _ =>
//        println("Can't find vault '%s'".format(vaultName))
//        cfg
//    }
//    newCfg.copy(vaultName = vaultName)
//    newCfg.save()
    0
  }



//  def main0(args: Array[String]) {
//    val action = args.headOption getOrElse "help"
//
//    lazy val dir = new File(".").getAbsoluteFile.getParentFile
//    lazy val cfg = readConfig(dir) getOrElse { createConfig(dir) }
//    lazy val client = {
//      val credentials = new PropertiesCredentials(new File(cfg.credentials))
//      new GlacierClient(credentials, cfg.endpoint)
//    }
//    // TODO: Handle vault name collision
//
//    action match {
//      case "create" => client.createVault(cfg.vaultName)
//      case "backup" => backup(dir, cfg, client)
//      case "restore" if args.size > 1 => restore(dir, cfg, args(1), client)
//      case "list" => list(cfg, client)
//      case _ => help()
//    }
//  }
//
//  def readConfig(dir: File): Option[Config] = {
//    val cfgFile = new File(dir, configFilename)
//    if (dir == null) None
//    else if (!cfgFile.exists()) readConfig(dir.getParentFile)
//    else {
//      val buffer = new Array[Byte](cfgFile.length().toInt)
//      new FileInputStream(cfgFile).read(buffer)
//      val json = new String(buffer)
//      Some(parse[Config](json))
//    }
//  }
//
//  def createConfig(dir: File): Config = {
//    val vaultName = dir.getName
//    val path = dir.getAbsolutePath + File.separator + configFilename
//    val config = Config(path, vaultName)
//    config.save()
//    config
//  }
//
//  def backup(dir: File, cfg: Config, client: GlacierClient) = {
//    val vault = client.getVault(cfg.vaultName).
//      getOrElse { client.createVault(cfg.vaultName) }
//    val root = new File(cfg.path).getParentFile
//    val index = cfg.archiveInfos map { info => info.desc -> info } toMap
//
//    val updated = Util.updatedFiles(
//      dir, index, relativize(root, _))
//
//    val newIndex = updated.foldLeft(index) { (index, file) =>
//      val path = relativize(root, file)
//      val hash = calculateTreeHash(file)
//      val info = vault.upload(file, hash, path)
//      index.get(path) foreach { info =>
//        vault.deleteArchive(info.archiveId)
//      }
//      val nextIndex = index + (path -> info)
//      cfg.copy(archiveInfos = nextIndex.values.toList).save()
//      nextIndex
//    }
//    cfg.copy(archiveInfos = newIndex.values.toList)
//  }
//
//  def list(cfg: Config, client: GlacierClient) = {
//    val vault = client.getVault(cfg.vaultName).
//      getOrElse { client.createVault(cfg.vaultName) }
//    vault.getInventory match {
//      case Right(archiveInfos) =>
//        archiveInfos.sortBy(_.desc) foreach { info => println(info.desc) }
//      case Left(jobId) =>
//        println("Inventory in progress (JobID: '%s')".format(jobId))
//    }
//  }
//
//  def restore(root: File, cfg: Config, vaultName: String, client: GlacierClient) = {
//    val newCfg = client.getVault(vaultName) match {
//      case Some(vault) => vault.getInventory match {
//        case Right(archiveInfos) =>
//          vault.download(root, archiveInfos)
//          cfg.copy(archiveInfos = archiveInfos)
//        case Left(jobId) =>
//          println("Restore requested but not yet ready")
//          println("(please redo this command later)")
//          cfg
//      }
//      case _ =>
//        println("Can't find vault '%s'".format(vaultName))
//        cfg
//    }
//    newCfg.copy(vaultName = vaultName)
//    newCfg.save()
//  }
}
