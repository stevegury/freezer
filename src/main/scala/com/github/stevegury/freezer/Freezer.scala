package com.github.stevegury.freezer

import com.github.stevegury.freezer.tasks.{Restore, Inventory, Init, Backup}
import java.io.File

import scala.collection._
import scala.io.StdIn

object Freezer {

  def help(): Int = {
    println("Usage: fz <command> [<args>]")
    println("Commands are:")
    println("  init       Initialize the directory as a freezer backup.")
    println("  backup     Backup the current directory (recursively).")
    println("  inventory  List the current (remote) state of the vault.")
    println("  restore    Restore the vault into the current directory.")
    println("  help <cmd> Provide more info .")
    println("  help       This message.")
    println("")
    println("See 'fz help <command>' to read about a specific command.")
    -1
  }

  def help(command: String): Int = {
    val msg = command.toLowerCase match {
      case "init" =>
        "Usage: fz init\n" +
        "    Initialize the current directory as a freezer backup.\n" +
        "    This will create a '.freezer' directory.\n" +
        "    By default fz will interactively ask you some parameters but you can also\n" +
        "    specify those on the command line:\n" +
        "      --name <vault-name>\n" +
        "      --creds <credential file> (default: ~/aws.glacier.credentials)\n" +
        "      --endpoint <url> (default: https://glacier.us-east-1.amazonaws.com)\n" +
        "      --exclusions <regex1,regex2> (comma separated regexes of exclusions)\n"

      case "backup" =>
        "Usage: fz backup\n" +
        "    Scan the current directory (and subdirectories), and upload/update/delete\n" +
        "    the files associated in the AWS Glacier vault.\n" +
        "    All the Files and directories that match the exclusion pattern won't be backup-ed."

      case "inventory" =>
        "Usage: fz inventory\n" +
        "    Request an inventory of the AWS Glacier vault associated with the current\n" +
        "    directory.\n" +
        "    The inventory usually takes about 4 hours to complete, in the meantime all\n" +
        "    subsequent call will return the AWS job-id of the inventory request.\n" +
        "    If the vault has just been created, you need to wait first for AWS to generate\n" +
        "    the initial inventory (it usually takes ~24 hours)."

      case "restore" =>
        "Usage: fz restore <vault-name>\n" +
        "    Create a 'vault-name' subdirectory and restore the vault in it.\n" +
        "    This happen in two steps, the first step is to request an inventory (which\n" +
        "    takes ~4 hours), then create one archive-retrieval request per file (which takes\n" +
        "    ~4 hours to complete).\n" +
        "    If the vault has just been created, you need to wait first for AWS to generate\n" +
        "    the initial inventory (it usually takes ~24 hours)."

      case _ => s"unknown command '$command'"
    }
    println(msg)
    -1
  }

  private val reporter: String => Unit = Console.out.println
  private def stdinReader(str: String): String = StdIn.readLine(str)

  def main(args: Array[String]): Unit = try {
    val dir = new File(".").getAbsoluteFile.getParentFile
    val (commands, opts) = parseOptions(args)

    val x = readConfig(dir)
    val exitCode = (commands, x) match {
      case ("init" :: Nil, Some(_)) =>
        error("init", "Already a freezer directory!")
      case ("init" :: Nil, None) =>
        init(dir, opts)

      case ("backup" :: Nil, None) =>
        error("backup", "Not a freezer directory! Use 'init' command to initialize a freezer directory.")
      case ("backup" :: Nil, Some((root, cfg))) =>
        backup(root, cfg)

      case ("inventory" :: Nil, None) =>
        error("inventory", "Not a freezer directory! Use 'init' command to initialize a freezer directory.")
      case ("inventory" :: Nil, Some((root, cfg))) =>
        inventory(cfg)

      case ("restore" :: xs, None) =>
        val optName = xs.headOption orElse opts.get("name")
        restore(dir, optName, opts)
      case ("restore" :: Nil, Some((root, cfg))) =>
        restore(root, opts.get("name"), opts)

      case ("help" :: cmd :: rest, _) => help(cmd)
      case _ => help()
    }

    System.exit(exitCode)
  } catch {
    case ex: Throwable =>
      reporter(s"Unexpected error: ${ex.getMessage}")
      ex.printStackTrace()
      System.exit(Int.MinValue)
  }

  private[freezer] def parseOptions(args: Array[String]): (List[String], Map[String, String]) = {
    val commands = mutable.ArrayBuffer.empty[String]
    val options = mutable.Map.empty[String, String]

    def loop(i: Int, option: Option[String]): Unit =
      if (i < args.length)
        (args(i), option) match {
          case (cmd, None) if cmd.startsWith("--") =>
            loop(i + 1, Some(cmd.drop(2)))
          case (cmd, Some(opt)) if cmd.startsWith("--") =>
            options += opt -> ""
            loop(i + 1, Some(cmd.drop(2)))
          case (cmd, Some(opt)) =>
            options += opt -> cmd
            loop(i + 1, None)
          case (cmd, None) =>
            commands += cmd
            loop(i + 1, None)
        }
      else if (option.isDefined)
        options += option.get -> ""

    loop(0, None)
    (commands.toList, options)
  }

  private def findRoot(dir: File): Option[File] = {
    val cfgDir = new File(dir, configDirname)
    if (dir == null)
      None
    else if (cfgDir.isDirectory)
      Some(cfgDir.getParentFile.getAbsoluteFile)
    else
      findRoot(dir.getParentFile)
  }

  private def readConfig(dir: File): Option[(File, Config)] = {
    findRoot(dir) map { root =>
      val config = new File(new File(root, configDirname), "config")
      (root, Config.load(config))
    }
  }

  private def error(action: String, txt: String) = {
    Console.err.println(s"freezer $action error: '$txt'")
    -1
  }

  private def init(dir: File, opts: Map[String, String]) = {
    val task = new Init(dir, opts, reporter, stdinReader)
    task.run()
  }

  private def backup(root: File, cfg: Config) = {
    // TODO: creating the client could be defer inside backup
    val client = new AwsGlacierClient(cfg)
    val vault = client.getVault(cfg.vaultName) getOrElse {
      throw new IllegalStateException(s"Unable to access vault '${cfg.vaultName}'!")
    }
    val task = new Backup(root, cfg, vault, reporter)
    task.run()
  }

  private def inventory(cfg: Config) = {
    val client = new AwsGlacierClient(cfg)
    val vault = client.getVault(cfg.vaultName) getOrElse {
      throw new IllegalStateException(s"Unable to access vault '${cfg.vaultName}'!")
    }

    val task = new Inventory(vault, reporter)
    task.run()
  }

  private def restore(dir: File, optName: Option[String], opts: Map[String, String]): Int = {
    // check is the subdirectory has already been initialized
    val alreadyPresentCfg = optName flatMap { name =>
      val configFile = new File(configDir(new File(dir, name)), "config")
      if (configFile.exists())
        Some(Config.load(configFile))
      else
        None
    }

    val cfg = alreadyPresentCfg getOrElse {
      // If not initialize it
      val cfg = Init.initConfig(dir, stdinReader,
        optName,
        opts.get("creds"),
        opts.get("endpoint"),
        opts.get("exclusions")
      )
      val restoreDir = new File(dir, cfg.vaultName)
      restoreDir.mkdir()
      statusDir(restoreDir).mkdirs()
      cfg.save(new File(configDir(restoreDir), "config"))
      cfg
    }

    val restoreDir = new File(dir, cfg.vaultName)
    val client = new AwsGlacierClient(cfg)
    val vault = client.getVault(cfg.vaultName).get

    val task = new Restore(restoreDir, vault, reporter)
    task.run()
  }
}
