package com.github.stevegury.freezer

import com.amazonaws.auth.PropertiesCredentials
import com.amazonaws.services.glacier.TreeHashGenerator.calculateTreeHash
import com.github.stevegury.freezer.Util.relativize
import java.io.{FilenameFilter, FileOutputStream, File}
import java.util.Properties

import com.github.stevegury.freezer.tasks.{Restore, Inventory, Init, Backup}

import scala.io.StdIn

object Freezer {

  def help() = {
    println("Usage: freezer <action>")
    println("  actions are:")
    println("   - init: Just initialize the directory as a freezer backup.")
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
        val init = new Init(dir)
        val cfg = init.initConfig()
        restore(dir, dir, cfg)
      case ("restore", Some((root, cfg))) =>
        restore(dir, root, cfg)
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
    val task = new Init(dir)
    task.run()
  }

  def backup(dir: File, root: File, cfg: Config) = {
    val client = clientFromConfig(cfg)
    val vault = client.getVault(cfg.vaultName) getOrElse {
      // TODO: this shouldn't happen, init should have created the Vault
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
      getOrElse { client.createVault(cfg.vaultName) } // TODO: ditto, this shouldn't happen

    val task = new Inventory(vault)
    task.run()
  }

  def restore(dir: File, root: File, cfg: Config): Int = {
    val client = clientFromConfig(cfg)
    val vault = client.getVault(cfg.vaultName).get

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

    val task = new Restore(vault)
    task.run()
  }
}
