package com.github.stevegury.freezer

import com.amazonaws.auth.PropertiesCredentials
import com.amazonaws.services.glacier.TreeHashGenerator
import com.codahale.jerkson.Json.{generate, parse}
import com.twitter.util.StorageUnit

import java.io.{FileOutputStream, FileInputStream, File}

case class ArchiveInfo(
  archiveId: String,
  desc: String,
  creationDate: String,
  size: StorageUnit,
  hash: String
)
case class Config(
  vaultName: String,
  files: Seq[ArchiveInfo] = Seq.empty,
  pendingJobIds: Seq[String] = Seq.empty
) {
  def save(output: File) {
    val fos = new FileOutputStream(output)
    fos.write(generate(this).getBytes)
    fos.close()
  }
}

object Freezer {
  /*private[this]*/ lazy val client = GlacierClient

  def parseArgs(args: Array[String]) = {
    // command: backup: backup the current dir
    //          check:  check the current dir with the list of files in the last inventory
    //                  if no inventory is available, request the creation of one
    //          restore <name>: restore the all the files in a vault
    //                  if no archives are available, request one
    // options: ???
  }

  def main(args: Array[String]) = {
    val root = new File("/Users/stevegury/tmp/toto")
    val cfgFile = new File(root, defaultConfigFilename.getName)
    val action = "backup"

    (action, readConfig(cfgFile)) match {
      case ("backup", None) =>
        val vaultName = root.getAbsoluteFile.getParentFile.getName
        Config(vaultName).save(cfgFile)
        backup(client.createVault(vaultName), root, Seq.empty)

      case ("backup", Some(cfg)) =>
        backup(client.createVault(cfg.vaultName), root, cfg.files)

      case _ => ???
    }
  }

  def backup(vault: Vault, root: File, previousState: Seq[ArchiveInfo]) = {
    val index = previousState map { info => info.desc -> info } toMap
    def scan(file: File) {
      if (file.isDirectory)
        file.listFiles() foreach { scan }
      else if (file.getName == ".freezer") {
        // skip
      } else {
        val path = relativize(root, file)
        if (index.contains(path) && !needsBackup(file, index(path))) {
          println("'%s' is up to date, skipping backup")
        } else {
          println("Uploading '%s'...".format(file.getAbsolutePath))
          //vault.upload(file, path)
        }
      }
    }

    scan(root)
  }

  /*private[this]*/ def needsBackup(file: File, info: ArchiveInfo): Boolean =
    (file.length() != info.size.inBytes) || (info.hash != TreeHashGenerator.calculateTreeHash(file))

  /*private[this]*/ def relativize(base: File, path: File): String = {
    def inCommon(a: List[String], b: List[String], res: List[String]): List[String] = (a, b) match {
      case (Nil, Nil) => res
      case (x :: xs, y :: ys) if x == y => inCommon(xs, ys, x :: res)
      case _ => res
    }

    val baseList = base.getAbsolutePath.split(File.separator).toList
    val pathList = path.getAbsolutePath.split(File.separator).toList
    val common = inCommon(baseList, pathList, Nil).size
    "../" * (baseList.size - common) + pathList.drop(common).mkString("/")
  }

  /*private[this]*/ def readConfig(cfgFile: File): Option[Config] = {
    if (cfgFile.exists()) {
      val buffer = new Array[Byte](cfgFile.length().toInt)
      new FileInputStream(cfgFile).read(buffer)
      val json = new String(buffer)
      Some(parse[Config](json))
    } else {
      None
    }
  }

  private[freezer] val defaultConfigFilename =
    new File(".freezer")
  private[freezer] val defaultCredentialsFilename =
    new File(System.getProperty("user.home"), ".aws.glacier.credentials")
  private[freezer] val defaultCredentials =
    new PropertiesCredentials(defaultCredentialsFilename)
  private[freezer] val defaultEndpoint = "https://glacier.us-east-1.amazonaws.com/"
}
