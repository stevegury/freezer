package com.github.stevegury.freezer

import com.codahale.jerkson.Json
import com.codahale.jerkson.Json.parse

import java.io.{FileInputStream, FileOutputStream, File, StringWriter}

object JsonPrettyPrinter extends Json {
  override def generate[A](obj: A): String = {
    val writer = new StringWriter
    val generator = factory.createJsonGenerator(writer)
    generator.useDefaultPrettyPrinter()
    generator.writeObject(obj)
    generator.close()
    writer.toString
  }
}

case class ArchiveInfo(
  archiveId: String,
  desc: String,
  creationDate: String,
  size: Long,
  hash: String
)

case class Config(
  path: String,
  vaultName: String,
  credentials: String = defaultCredentialsFilename,
  endpoint: String = defaultEndpoint,
  exclusions: Seq[String] = Seq(".*\\.DS_Store$"),
  archiveInfos: Seq[ArchiveInfo] = Seq.empty
) {
  def save() {
    import JsonPrettyPrinter.generate

    val fos = new FileOutputStream(new File(path))
    fos.write(generate(this).getBytes)
    fos.close()
  }
}
