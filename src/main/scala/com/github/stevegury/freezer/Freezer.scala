package com.github.stevegury.freezer

import com.amazonaws.auth.{AWSCredentials, PropertiesCredentials}
import com.amazonaws.services.glacier.{TreeHashGenerator, AmazonGlacierClient}
import com.amazonaws.services.glacier.model._
import com.amazonaws.services.glacier.transfer.ArchiveTransferManager
import com.codahale.jerkson.Json.parse
import com.twitter.util._
import com.twitter.conversions.time._

import java.io.{FileInputStream, File}

import scala.collection.JavaConversions._

case class ArchiveInfo(
  archiveId: String,
  desc: String,
  creationDate: String,
  size: StorageUnit,
  hash: String
)
case class Config(
  vaultName: String,
  files: Seq[ArchiveInfo],
  pendingJobIds: Seq[String]
)

object Freezer {
  /*private[this]*/ lazy val client = GlacierClient

  sealed abstract class Action
  case class Backup() extends Action
  case class Check() extends Action
  //case class Restore(vaultName: String) extends Action

  def main(args: Array[String]) = {
    // options: backup: backup the current dir
    //          check:  check the current dir with the list of files in the last inventory
    val action: Action = Backup()

    val cfgFile = defaultConfigFilename
    readConfig(cfgFile) match{
      case Some(cfg) =>
        (client.getVault(cfg.vaultName), action) match {
          case (Some(vault), Backup()) => backup(vault, cfg.files)
          case (None, Backup()) => println("Vault '%s' doesn't exists!".format(cfg.vaultName))
          case _ => println("Not yet implemented")
        }
      case _ =>
        Console.err.println("No configuration file found! ('%s')".format(cfgFile.getAbsolutePath))
        cfgFile.getParentFile.mkdirs()
        cfgFile.createNewFile()
    }
  }

  def backup(vault: Vault, previousState: Seq[ArchiveInfo]) = {
    val root = new File(".")
    val index = previousState map { info => info.desc -> info } toMap
    def scan(file: File) {
      if (file.isDirectory)
        file.listFiles() foreach { scan }
      else {
        val path = relativize(root, file)
        if (index.contains(path) && !needsBackup(file, index(path))) {
          println("'%s' is up to date, skipping backup")
        } else {
          println("Uploading '%s'...")
          vault.upload(file, path)
        }
      }
    }

    scan(root)
  }

  /*private[this]*/ def needsBackup(file: File, info: ArchiveInfo): Boolean =
    (file.length() != info.size.inBytes) || (info.hash != TreeHashGenerator.calculateTreeHash(file))

  /*private[this]*/ def relativize(base: File, path: File): String = {
    val baseList = base.getAbsolutePath.split(File.separator).toList
    val pathList = path.getAbsolutePath.split(File.separator).toList

    def common(a: List[String], b: List[String], res: List[String]): List[String] =
      if (a.isEmpty || b.isEmpty) res
      else if (a.head == b.head) common(a.tail, b.tail, a.head :: res)
      else res

    val cmn = common(baseList, pathList, Nil).size
    "../" * (baseList.size - cmn) + pathList.drop(cmn).mkString("/")
  }

  /*private[this]*/ def readConfig(cfgFile: File): Option[Config] = {
    Option(cfgFile.exists()) map { _ =>
      val buffer = new Array[Byte](cfgFile.length().toInt)
      new FileInputStream(cfgFile).read(buffer)
      val json = new String(buffer)
      parse[Config](json)
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

class GlacierClient(val credentials: AWSCredentials, val endpoint: String) {
  /*private[this]*/ val client = new AmazonGlacierClient(credentials)

  /**
   * List the vaults
   */
  def listVaults: Seq[Vault] = {
    val vaultList = client.listVaults(new ListVaultsRequest).getVaultList
    vaultList map { desc => new Vault(client, credentials, desc) }
  }

  /**
   * Create a vault
   */
  def createVault(name: String): Vault = {
    val req = new CreateVaultRequest(name)
    client.createVault(req)
    getVault(name).get
  }

  /**
   * Get the vault
   */
  def getVault(name: String): Option[Vault] = listVaults filter { _.name == name } headOption
}

object GlacierClient extends GlacierClient(Freezer.defaultCredentials, Freezer.defaultEndpoint)

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
    val req = new InitiateJobRequest(name, new JobParameters("JSON", "inventory-retrieval", null, "desc"))
    val jobId = client.initiateJob(req).getJobId
    val timeout = 1.hour

    def waitForJob(begining: Time = Time.now) {
      val elapsed = Time.now - begining
      if (elapsed < timeout)
        throw new TimeoutException("Reach timeout while waiting for job " + jobId)
      else {
        val desc = client.describeJob(new DescribeJobRequest(name, jobId))
        if (!desc.isCompleted) {
          Thread.sleep(60*1000)
          waitForJob(begining)
        }
      }
    }
    waitForJob()

    val r2 = new GetJobOutputRequest().withVaultName(name).withJobId(jobId)
    val out = client.getJobOutput(r2)
    out.getBody
  }
}
