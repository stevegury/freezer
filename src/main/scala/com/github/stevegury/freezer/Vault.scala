package com.github.stevegury.freezer

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.glacier.AmazonGlacierClient
import com.amazonaws.services.glacier.model.DescribeVaultOutput
import com.amazonaws.services.glacier.transfer.ArchiveTransferManager
import com.twitter.util.StorageUnit

import java.io.File

class Vault(
  client: AmazonGlacierClient,
  credentials: AWSCredentials,
  val name: String,
  val creationDate: String,
  val inventoryDate: String,
  val numberOfArchive: Long,
  val size: StorageUnit,
  val arn: String) {
  /*private[this]*/ val atm = new ArchiveTransferManager(client, credentials)

  def this(client: AmazonGlacierClient, credentials: AWSCredentials, desc: DescribeVaultOutput) =
    this(client, credentials, desc.getVaultName, desc.getCreationDate, desc.getLastInventoryDate,
        desc.getNumberOfArchives, new StorageUnit(desc.getSizeInBytes), desc.getVaultARN)

  def upload(file: File, desc: String) = atm.upload(name, desc, file)

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
  def getInventory = {
    // val req = new InitiateJobRequest(name, new JobParameters("JSON", "inventory-retrieval", null, "desc"))
    // val jobId = client.initiateJob(req).getJobId
    // val timeout = 1.hour

    // def waitForJob(begining: Time = Time.now) {
    //   val elapsed = Time.now - begining
    //   if (elapsed < timeout)
    //     throw new TimeoutException("Reach timeout while waiting for job " + jobId)
    //   else {
    //     val desc = client.describeJob(new DescribeJobRequest(name, jobId))
    //     if (!desc.isCompleted) {
    //       Thread.sleep(60*1000)
    //       waitForJob(begining)
    //     }
    //   }
    // }
    // waitForJob()

    // val r2 = new GetJobOutputRequest().withVaultName(name).withJobId(jobId)
    // val out = client.getJobOutput(r2)
    // out.getBody
  }
}
