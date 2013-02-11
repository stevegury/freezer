package com.github.stevegury.freezer

import java.io.{FileInputStream, FileOutputStream}
import java.util.Properties

case class ArchiveInfo(
  archiveId: String,
  desc: String,
  creationDate: String,
  size: Long,
  hash: String
) {
  def save(path: String) {
    val p = new Properties()
    p.setProperty("archiveId", archiveId)
    p.setProperty("desc", desc)
    p.setProperty("creationDate", creationDate)
    p.setProperty("size", size.toString)
    p.setProperty("hash", hash)

    p.store(new FileOutputStream(path), "ArchiveInfo for file '" + desc + "'")
  }
}

object ArchiveInfo {
  def load(path: String): ArchiveInfo = {
    val p = new Properties()
    p.load(new FileInputStream(path))

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
  path: String,
  vaultName: String,
  credentials: String = defaultCredentialsFilename,
  endpoint: String = defaultEndpoint,
  exclusions: Seq[String] = Seq(".*\\.DS_Store$")
) {
  def save() {
    val p = new Properties()
    p.setProperty("path", path)
    p.setProperty("vaultName", vaultName)
    p.setProperty("credentials", credentials)
    p.setProperty("endpoint", endpoint)
    p.setProperty("exclusions", exclusions.mkString(","))

    p.store(new FileOutputStream(path), "Config file for vault '" + vaultName + "'")
  }
}

object Config {
  def load(path: String): Config = {
    val p = new Properties()
    p.load(new FileInputStream(path))

    Seq("vaultName", "credentials", "endpoint", "path") foreach {
      name => require(null != p.getProperty(name))
    }

    Config(
      path = path,
      vaultName = p.getProperty("vaultName"),
      credentials = p.getProperty("credentials"),
      endpoint = p.getProperty("endpoint"),
      exclusions = p.getProperty("exclusions").split(",")
    )
  }
}