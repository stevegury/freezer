package com.github.stevegury.freezer

import com.amazonaws.auth.PropertiesCredentials
import com.amazonaws.services.glacier.TreeHashGenerator.calculateTreeHash
import com.github.stevegury.freezer.Util.relativize
import java.io.{FilenameFilter, FileOutputStream, File}
import java.util.Properties

object Freezer {

  def help() = {
    println("Usage: freezer <action>")
    println("  actions are:")
    println("   - create: Just create the '.freezer' file.")
    println("   - backup: backup the current (and all subdirectories).")
    println("   - restore <vaultName>: restore the vaultName into the current directory.")
    println("   - list: List the current state of the vault from the server.")
    -1
  }

  def readConfig(dir: File): Option[Config] = {
    val cfgFile = new File(dir, configFilename)
    if (dir == null)
      None
    else if (!cfgFile.exists())
      readConfig(dir.getParentFile)
    else
      Some(Config.load(cfgFile.getAbsolutePath))
  }


  def main(args: Array[String]) {
    val action = args.headOption getOrElse "help"
    val dir = new File(".").getAbsoluteFile.getParentFile
    val cfg = readConfig(dir)

    val exitCode = (action, cfg) match {
      case ("init", Some(cfg)) =>
        error("Already a freezer directory!")
      case ("init", None) => init(dir)
      case ("backup", None) =>
        error("Not a freezer directory!")
      case ("backup", Some(cfg)) =>
        backup(dir, cfg)
      case ("inventory", None) =>
        error("Not a freezer directory!")
      case ("inventory", Some(cfg)) =>
        inventory(cfg)
      case ("restore", None) =>
        error("Do a freezer init, before requesting a restore!")
      case ("restore", Some(cfg)) =>
        restore()
        0
      case _ => help()
    }
    System.exit(exitCode)
  }

  def error(txt: String) = {
    Console.err.println("Already a freezer directory!")
    -1
  }

  def clientFromConfig(cfg: Config): GlacierClient = {
    val credentials = new PropertiesCredentials(new File(cfg.credentials))
    new GlacierClient(credentials, cfg.endpoint)
  }

  def init(dir: File) = {
    def getOrDefault(txt: String, default: String) = {
      val userInput = Console.readLine(txt + " [%s]: ", default)
      if ("" != userInput) userInput else default
    }
    val path = dir.getAbsolutePath + File.separator + configFilename
    val config = Map(
      "path" -> path,
      // TODO: Handle vault name collision
      "vaultName" -> getOrDefault("VaultName", dir.getName),
      "credentials" -> getOrDefault("Credential location", defaultCredentialsFilename),
      "endpoint" -> getOrDefault("Endpoint", defaultEndpoint),
      "exclusions" -> getOrDefault("Exclusions (comma separated)", ".*\\.DS_Store$")
    )

    new File(path, "status").mkdir()

    val p = new Properties()
    config foreach {
      case (name, value) => p.setProperty(name, value)
    }
    p.store(new FileOutputStream(path), "Config file for vault '" + config("vaultName") + "'")
    0
  }

  def backup(dir: File, cfg: Config) = {
    val client = clientFromConfig(cfg)
    val vault = client.getVault(cfg.vaultName) getOrElse { client.createVault(cfg.vaultName) }
    val root = new File(cfg.path).getParentFile
    val rootStatusDir = new File(dir.getAbsolutePath + File.separator + "status")

    def loop(dir: File, statusDir: File) {
      // TODO: check for exclusion (pattern matching)
      val files = dir.listFiles.filter(_.isFile).xmap(f => f.getName -> f).toMap
      val statuses = statusDir.listFiles.filter(_.isFile).map(f => f.getName -> f).toMap

      val newFiles = (files.keySet -- statuses.keySet).map(files)
      val deletedFiles = (statuses.keySet -- files.keySet).map(statuses)

      def upload(file: File, hash: String, relativePath: String) = {
        val archInfo = vault.upload(file, hash, relativePath)
        archInfo.save(rootStatusDir + File.separator + relativePath)
      }

      newFiles foreach { f =>
        val path = relativize(f, root)
        val hash = calculateTreeHash(f)
        vault.upload(f, hash, path)
      }
      deletedFiles foreach { archInfoPath =>
        val archiveInfo = ArchiveInfo.load(archInfoPath.getAbsolutePath)
        vault.deleteArchive(archiveInfo.archiveId)
      }
      files foreach { case (name, file) =>
        val archiveInfo = ArchiveInfo.load(file.getAbsolutePath)
        val path = relativize(file, root)
        lazy val hash = calculateTreeHash(file)
        if (file.length() != archiveInfo.size &&
          calculateTreeHash(file) != archiveInfo.hash) {
          val archInfo = vault.upload(file, hash, path)
          archInfo.save(statuses(name).getAbsolutePath)
          vault.deleteArchive(archInfo.archiveId)
        }
      }

      // TODO: what about deleted directory (only present in .freezer/status/...)?
      dir.listFiles().filter(_.isDirectory) foreach { d =>
        loop(d, new File(statusDir, d.getName))
      }
    }

    loop(dir, rootStatusDir)
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

  def restore(cfg: Config) = {
    val client = clientFromConfig(cfg)
    val vault = client.getVault(cfg.vaultName).get()
    val root = new File(cfg.path).getParentFile

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
