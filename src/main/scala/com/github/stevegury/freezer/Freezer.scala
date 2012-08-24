package com.github.stevegury.freezer

import com.amazonaws.auth.{AWSCredentials, PropertiesCredentials}
import com.amazonaws.services.glacier.AmazonGlacierClient
import com.amazonaws.services.glacier.model.{
  CreateVaultRequest, DescribeVaultOutput, ListVaultsRequest, UploadArchiveRequest
}


import java.io.{File, FileInputStream}

import scala.collection.JavaConversions._


object Freezer {
  def main(args: Array[String]) = {
    println("Freezer")
  }

  private[freezer] val defaultCredentialsFilename =
    new File(System.getProperty("user.home"), ".aws.glacier.credentials")
  private[freezer] val defaultCredentials =
    new PropertiesCredentials(defaultCredentialsFilename)
}

class GlacierClient(credentials: AWSCredentials) {
  /*private[this]*/ val client = new AmazonGlacierClient(credentials)

  /**
   * List the vaults
   */
  def listVaults: Seq[Vault] = {
    val vaultList = client.listVaults(new ListVaultsRequest).getVaultList
    vaultList map { desc => new Vault(client, desc) }
  }

  /**
   * Create a vault
   */
  def createVault(name: String): Vault = {
    val req = new CreateVaultRequest(name)
    client.createVault(req)
    listVaults filter { _.name == name } head
  }
}

object GlacierClient extends GlacierClient(Freezer.defaultCredentials)

class Vault(
  client: AmazonGlacierClient,
  val name: String,
  val creationDate: String,
  val inventoryDate: String,
  val numberOfArchive: Long,
  val size: Long, // use util Data
  val arn: String) {

  def this(client: AmazonGlacierClient, desc: DescribeVaultOutput) =
    this(client, desc.getVaultName, desc.getCreationDate, desc.getLastInventoryDate,
        desc.getNumberOfArchives, desc.getSizeInBytes, desc.getVaultARN)

  def upload(file: File, desc: String) = {
    val checksum = ""
    val inputStream = new FileInputStream(file)
    val req = new UploadArchiveRequest(name, desc, checksum, inputStream)
    client.uploadArchive(req)
  }
}
