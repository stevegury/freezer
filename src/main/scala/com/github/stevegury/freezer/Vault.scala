package com.github.stevegury.freezer

import java.text.SimpleDateFormat
import java.util.Date

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.glacier.AmazonGlacierClient
import com.amazonaws.services.glacier.model._
import com.amazonaws.services.glacier.transfer.ArchiveTransferManager
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import java.io._

import scala.collection.JavaConversions._

case class AmazonArchiveInfo(
  ArchiveId: String,
  ArchiveDescription: String,
  CreationDate: String,
  Size: Int,
  SHA256TreeHash: String
)

case class InventoryRes(
  VaultARN: String,
  InventoryDate: String,
  ArchiveList: Seq[AmazonArchiveInfo]
)


class Vault(
  client: AmazonGlacierClient,
  credentials: AWSCredentials,
  val name: String,
  val creationDate: String,
  val inventoryDate: String,
  val numberOfArchive: Long,
  val size: Long,
  val arn: String) {

  def this(client: AmazonGlacierClient, credentials: AWSCredentials, desc: DescribeVaultOutput) =
    this(client, credentials, desc.getVaultName, desc.getCreationDate, desc.getLastInventoryDate,
        desc.getNumberOfArchives, desc.getSizeInBytes, desc.getVaultARN)

  private[this] val atm = new ArchiveTransferManager(client, credentials)
  private[this] val jsonReader = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper
  }
  private[this] val dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")

  def upload(file: File, hash: String, desc: String) = {
    //val res = atm.upload(name, desc, file)
    val now = new Date
    ArchiveInfo(
      "",//res.getArchiveId,
      desc,
      dateFormat.format(now),
      file.length(),
      hash
    )
  }

  def deleteArchive(archiveId: String) {
    //client.deleteArchive(new DeleteArchiveRequest(name, archiveId))
  }

  def download(root: File, archiveInfos: Seq[ArchiveInfo]) = {
    val currentJobIds = listArchiveRetrievalJobs filter {
      job => archiveInfos.map(_.archiveId).contains(job.getArchiveId) }
    val newJobs = archiveInfos filterNot { archiveInfo =>
      currentJobIds.find(_.getArchiveId == archiveInfo.archiveId).isDefined
    }
    val (completed, inProgress) = currentJobIds partition { _.getCompleted }

    newJobs foreach { info =>
      val jobId = requestDownload(info.archiveId, info.desc)
      println("Requesting download for: '%s'".format(info.desc))
      jobId
    }
    inProgress foreach { jobDesc =>
      val path = jobDesc.getJobDescription
      println("Server still preparing download of: '%s'".format(path))
    }
    completed foreach { jobDesc =>
      val path = jobDesc.getJobDescription
      val output = new File(root, path)
      if (output.exists())
        println("Skipping '%s' (already present)".format(path))
      else {
        println("Downloading '%s'...".format(path))
        downloadToFile(jobDesc.getJobId, output)
      }
    }
  }

  def requestDownload(archiveId: String, desc: String): String = {
    val params = new JobParameters(null, "archive-retrieval", archiveId, desc)
    val req = new InitiateJobRequest(name, params)
    client.initiateJob(req).getJobId
  }

  def downloadToFile(jobId: String, file: File) {
    file.getParentFile.mkdirs()
    val res = client.getJobOutput(new GetJobOutputRequest(name, jobId, ""))

    val is = new BufferedInputStream(res.getBody)
    val os = new BufferedOutputStream(new FileOutputStream(file))

    val buf = new Array[Byte](1024*1024)
    var n = is.read(buf)
    while(n > 0) {
      os.write(buf, 0, n)
      n = is.read(buf)
    }
    is.close()
    os.close()
  }

  def listJobs: Seq[GlacierJobDescription] = client.listJobs(new ListJobsRequest(name)).getJobList

  def listInventoryRetrievalJobs = listJobs filter { job => job.getAction == "InventoryRetrieval" }

  def listArchiveRetrievalJobs = listJobs filter { job => job.getAction == "ArchiveRetrieval" }

  /**
   * Retrieve the inventory from the AWS server
   * We only allow a max of 1 pending inventory
   * Return either the jobId of the pending or the result Seq[ArchiveInfo]
   */
  def getInventory: Either[String, Seq[ArchiveInfo]] = {
    val (completedJobs, jobsInProgress) =
      listInventoryRetrievalJobs partition { _.getCompleted }
    require(jobsInProgress.size + completedJobs.size <= 1)

    (completedJobs, jobsInProgress) match {
      case (Seq(), Seq()) => Left(requestInventory())
      case (Seq(), inProgress) => Left(inProgress.head.getJobId)
      case (completed, _) => Right(retrieveInventory(completed.head.getJobId))
    }
  }

  def requestInventory(): String = {
    val params = new JobParameters("JSON", "inventory-retrieval", null, "desc")
    val req = new InitiateJobRequest(name, params)
    client.initiateJob(req).getJobId
  }

  def retrieveInventory(jobId: String): Seq[ArchiveInfo] = {
    val res = client.getJobOutput(new GetJobOutputRequest(name, jobId, ""))
    val is = new InputStreamReader(res.getBody())

    val strBuffer = new StringBuffer()
    val buf = new Array[Char](4*1024)
    var n = is.read(buf)
    while(n > 0) {
      strBuffer.append(buf, 0, n)
      n = is.read(buf)
    }
    is.close()
    val output = strBuffer.toString

    // Ex of response:
    // {
    //   "VaultARN":"arn:aws:glacier:us-east-1:484086368941:vaults/test",
    //   "InventoryDate":"2012-08-26T05:56:15Z",
    //   "ArchiveList":[
    //     {
    //       "ArchiveId":"EldjOvusbDYJkzB1vLOopCa8yHXCh41RfxbGnd9AWVInN5wyQqDZ4bhkMPA91eOTtoNBpflw__IAfKA57D-JNqoV2Lz3DXFVQZwn8lifAcXcsLUNybDOK96G173itYuSVKSEwsT6dA",
    //       "ArchiveDescription":"a.html",
    //       "CreationDate":"2012-08-25T16:48:29Z",
    //       "Size":15097,
    //       "SHA256TreeHash":"e75b935410e507c797f5beccaf7c4c2a06f179a2d7b8dc08bcbf6e4b120b21d7"
    //     }
    //   ]
    // }
    jsonReader.readValue(output, classOf[InventoryRes]).ArchiveList map { amazonArchiveInfo =>
      ArchiveInfo(
        amazonArchiveInfo.ArchiveId,
        amazonArchiveInfo.ArchiveDescription,
        amazonArchiveInfo.CreationDate,
        amazonArchiveInfo.Size,
        amazonArchiveInfo.SHA256TreeHash
      )
    }
  }
}
