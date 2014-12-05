package com.github.stevegury.freezer

import java.io.{FileOutputStream, FileInputStream, File}
import scala.collection.mutable
import scala.util.Random
import com.amazonaws.services.glacier.TreeHashGenerator._

class TestingVault extends Vault {
  private[this] val rng = new Random()
  private[this] val content = mutable.Map.empty[String, (Array[Byte], String)]
  private[this] var jobId: Option[String] = None

  private[freezer] def getContent = synchronized { content }
  private[freezer] def getCurrentJobId = synchronized { jobId }

  def getName: String = "TestingVault"

  def upload(file: File, hash: String, desc: String): ArchiveInfo = synchronized {
    require(file.exists())
    val computedHash = calculateTreeHash(file)
    require(hash == computedHash)
    val fileContent = readContent(file)
    val archiveId = rng.nextString(64)
    content += archiveId -> (fileContent, desc)
    ArchiveInfo(
      archiveId,
      hash = calculateTreeHash(file),
      path = desc
    )
  }

  def deleteArchive(archiveId: String): Unit = synchronized {
    content -= archiveId
  }

  def download(root: File, archiveInfos: Seq[ArchiveInfo]): Unit = synchronized {
    for (info <- archiveInfos) {
      val output = new FileOutputStream(new File(root, info.path))
      val (fileContent, path) = content(info.archiveId)
      require(path == info.path)
      output.write(fileContent)
      output.close()
    }
  }

  def getInventory: Either[String, Seq[ArchiveInfo]] = synchronized {
    jobId match {
      case Some(_) =>
        val archiveInfos = content.toSeq map { case (id, (fileContent, path)) =>
          val list = java.util.Arrays.asList(fileContent)
          val hash = calculateTreeHash(list)
          ArchiveInfo(id, hash, path)
        }
        jobId = None
        Right(archiveInfos)
      case None =>
        val id = rng.nextString(32)
        jobId = Some(id)
        Left(id)
    }
  }

  private[this] def readContent(file: File): Array[Byte] = {
    require(file.length() < Int.MaxValue)
    val input = new FileInputStream(file)
    val buffer = new Array[Byte](file.length().toInt)
    input.read(buffer)
    input.close()
    buffer
  }
}
