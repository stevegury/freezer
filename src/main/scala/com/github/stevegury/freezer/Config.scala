package com.github.stevegury.freezer

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.Properties

import scala.util.matching.Regex

case class ArchiveInfo(
  archiveId: String,
  desc: String,
  creationDate: String,
  size: Long,
  hash: String
) {
  def save(outputFile: File) {
    val p = new Properties()
    p.setProperty("archiveId", archiveId)
    p.setProperty("desc", desc)
    p.setProperty("creationDate", creationDate)
    p.setProperty("size", size.toString)
    p.setProperty("hash", hash)

    p.store(new FileOutputStream(outputFile), "ArchiveInfo for file '" + desc + "'")
  }
}

object ArchiveInfo {
  def load(inputFile: File): ArchiveInfo = {
    val p = new Properties()
    val input = new FileInputStream(inputFile)
    p.load(input)
    input.close()

    Seq("archiveId", "desc", "creationDate", "size", "hash") foreach {
      name => require(null != p.getProperty(name))
    }

    ArchiveInfo(
      archiveId = p.getProperty("archiveId"),
      desc = p.getProperty("desc"),
      creationDate = p.getProperty("creationDate"),
      size = p.getProperty("size").toInt,
      hash = p.getProperty("hash")
    )
  }
}

case class Config(
  vaultName: String,
  credentials: String = defaultCredentialsFilename,
  endpoint: String = defaultEndpoint,
  exclusions: Seq[Regex] = Seq("\\.DS_Store".r, "\\.git".r)
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