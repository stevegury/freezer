package com.github.stevegury.freezer

import java.io.{FileOutputStream, FileInputStream, File}
import com.amazonaws.services.glacier.model.GlacierJobDescription

import scala.collection.mutable
import scala.util.Random
import com.amazonaws.services.glacier.TreeHashGenerator._

class TestingVault extends Vault {
  private[this] val rng = new Random()
  private[this] val content = mutable.Map.empty[String, (Array[Byte], ArchiveInfo)]
  private[this] var jobId: Option[String] = None

  private[this] val inventoryRetrievalJobs = mutable.Map.empty[String, GlacierJobDescription]
  private[this] val archiveRetrievalJobs = mutable.Map.empty[String, GlacierJobDescription]

  private[freezer] def getContentPath = synchronized {
    content.toSeq map { case (_, (_, archiveInfo)) => archiveInfo.path }
  }
  private[freezer] def getCurrentJobId = synchronized { jobId }

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

  // TODO: fix the reporter
  def download(root: File, archiveInfos: Seq[ArchiveInfo]): Unit = synchronized {
    for (info <- archiveInfos) {
      val output = new FileOutputStream(new File(root, info.path))
      val (fileContent, path) = content(info.archiveId)
      require(path == info.path)
      output.write(fileContent)
      output.close()
    }
  }

  def downloadToFile(jobId: String, output: File): Unit = {
    val desc = archiveRetrievalJobs(jobId)
    val (data, archInfo) = content(desc.getArchiveId)
    val out = new FileOutputStream(output)
    out.write(data)
    out.close()
  }

  def requestDownload(archiveId: String, path: String): String = {
    val jobId = rng.nextInt().toString
    val jobDesc = new GlacierJobDescription()
      .withArchiveId(archiveId)
      .withJobId(jobId)
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

    inventoryRetrievalJobs += jobId -> jobDesc
    jobId
  }

  def retrieveInventory(jobId: String): Seq[ArchiveInfo]= {
    require(inventoryRetrievalJobs.contains(jobId))
    content.toSeq map { case (_, (_, archiveInfo)) => archiveInfo }
  }

  def getInventory(now: Boolean): Either[String, Seq[ArchiveInfo]] = synchronized {
    //TODO: handle `now`
    jobId match {
      case Some(id) =>
        val archiveInfos = retrieveInventory(id)
        jobId = None
        Right(archiveInfos)
      case None =>
        val id = rng.nextInt().toString
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
