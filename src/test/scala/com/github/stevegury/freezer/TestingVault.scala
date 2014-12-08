package com.github.stevegury.freezer

import java.io.{FileOutputStream, FileInputStream, File}
import com.amazonaws.services.glacier.model.GlacierJobDescription

import scala.collection.mutable
import scala.util.Random
import com.amazonaws.services.glacier.TreeHashGenerator._

class TestingVault extends Vault {
  private[this] val rng = new Random()
  private[this] val content = mutable.Map.empty[String, (Array[Byte], ArchiveInfo)]

  private[this] var i = 0
  private[this] val inventoryRetrievalJobs = mutable.Map.empty[String, GlacierJobDescription]
  private[this] val archiveRetrievalJobs = mutable.Map.empty[String, GlacierJobDescription]

  private[freezer] def getContentPath = synchronized {
    content.toSeq map { case (_, (_, archiveInfo)) => archiveInfo.path }
  }
  private[freezer] def deleteAllJobs() = synchronized {
    inventoryRetrievalJobs.clear()
    archiveRetrievalJobs.clear()
  }

  def getName: String = "TestingVault"

  def upload(file: File, hash: String, desc: String): ArchiveInfo = synchronized {
    require(file.exists())
    val computedHash = calculateTreeHash(file)
    require(hash == computedHash)
    val fileContent = readContent(file)
    val archiveId = rng.nextInt().toString
    val archInfo = ArchiveInfo(
      archiveId,
      hash = calculateTreeHash(file),
      path = desc
    )
    content += archiveId -> (fileContent, archInfo)
    archInfo
  }

  def deleteArchive(archiveId: String): Unit = synchronized {
    content -= archiveId
  }

  def downloadToFile(jobId: String, output: File): Unit = {
    output.getParentFile.mkdirs()
    val desc = archiveRetrievalJobs(jobId)
    val (data, archInfo) = content(desc.getArchiveId)
    val out = new FileOutputStream(output)
    out.write(data)
    out.close()
  }

  def requestDownload(archiveId: String, path: String): String = {
    val jobId = rng.nextInt().toString
    val (data, archInfo) = content(archiveId)
    val jobDesc = new GlacierJobDescription()
      .withArchiveId(archiveId)
      .withJobId(jobId)
      .withCreationDate(s"2012-0$i-20T17:03:43.221Z")
      .withCompleted(true)
      .withAction("ArchiveRetrieval")
      .withJobDescription(path)
      .withArchiveSHA256TreeHash(archInfo.hash)
    i += 1
    archiveRetrievalJobs += jobId -> jobDesc
    jobId
  }

  def listJobs: Seq[GlacierJobDescription] =
    (inventoryRetrievalJobs.values ++ archiveRetrievalJobs.values).toSeq

  def listInventoryRetrievalJobs: Seq[GlacierJobDescription] = inventoryRetrievalJobs.values.toSeq

  def listArchiveRetrievalJobs: Seq[GlacierJobDescription] = archiveRetrievalJobs.values.toSeq

  def requestInventory(): String = {
    val jobId = rng.nextInt().toString
    val jobDesc = new GlacierJobDescription()
      .withJobId(jobId)
      .withCreationDate(s"2012-0$i-20T17:03:43.221Z")
      .withCompleted(true)
      .withAction("InventoryRetrieval")
    i += 1

    inventoryRetrievalJobs += jobId -> jobDesc
    jobId
  }

  def retrieveInventory(jobId: String): Seq[ArchiveInfo]= {
    require(inventoryRetrievalJobs.contains(jobId))
    content.toSeq map { case (_, (_, archiveInfo)) => archiveInfo }
  }

  def getInventory: Either[String, Seq[ArchiveInfo]] = synchronized {
    inventoryRetrievalJobs.headOption match {
      case Some((id, _)) =>
        val archiveInfos = retrieveInventory(id)
        Right(archiveInfos)
      case None =>
        val id = requestInventory()
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
