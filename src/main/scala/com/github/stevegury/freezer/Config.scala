package com.github.stevegury.freezer

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.Properties
import scala.util.matching.Regex

case class ArchiveInfo(
  archiveId: String,
  hash: String,
  path: String // not saved to file
) {
  def save(outputFile: File) {
    val p = new Properties()
    p.setProperty("archiveId", archiveId)
    p.setProperty("hash", hash)

    p.store(new FileOutputStream(outputFile), "")
  }
}

object ArchiveInfo {
  def load(inputFile: File, relativePath: String): ArchiveInfo = {
    val p = new Properties()
    val input = new FileInputStream(inputFile)
    p.load(input)
    input.close()

    Seq("archiveId", "hash") foreach {
      name => require(null != p.getProperty(name))
    }

    ArchiveInfo(
      archiveId = p.getProperty("archiveId"),
      hash = p.getProperty("hash"),
      path = relativePath
    )
  }
}

case class Config(
  vaultName: String,
  credentials: String = defaultCredentialsFilename,
  endpoint: String = defaultEndpoint,
  exclusions: Seq[Regex] = Seq.empty
) {
  def save(outputFile: File) {
    val p = new Properties()
    p.setProperty("vaultName", vaultName)
    p.setProperty("credentials", credentials)
    p.setProperty("endpoint", endpoint)
    p.setProperty("exclusions", exclusions.map(_.regex).mkString(","))

    val output = new FileOutputStream(outputFile)
    p.store(output, "Config file for vault '" + vaultName + "'")
    output.close()
  }
}

object Config {
  def load(inputFile: File): Config = {
    require(inputFile.exists())
    val p = new Properties()
    val input = new FileInputStream(inputFile)
    p.load(input)
    input.close()

    Seq("vaultName", "credentials", "endpoint", "exclusions") foreach {
      name => require(null != p.getProperty(name))
    }

    Config(
      vaultName = p.getProperty("vaultName"),
      credentials = p.getProperty("credentials"),
      endpoint = p.getProperty("endpoint"),
      exclusions = p.getProperty("exclusions").split(",").map(_.r)
    )
  }
}