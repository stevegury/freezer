package com.github.stevegury.freezer

import com.amazonaws.auth.{AWSCredentials, PropertiesCredentials}
import com.amazonaws.services.glacier.AmazonGlacierClient
import com.amazonaws.services.glacier.model.{
  DescribeVaultOutput, ListVaultsRequest
}


import java.io.File

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
   * list the vaults
   */
  def listVaults: Seq[Vault] = {
    val vaultList = client.listVaults(new ListVaultsRequest).getVaultList
    vaultList map { desc => new Vault(client, desc) }
  }
}

object GlacierClient extends GlacierClient(Freezer.defaultCredentials)

class Vault(
  client: AmazonGlacierClient,
  name: String,
  creationDate: String,
  inventoryDate: String,
  numberOfArchive: Long,
  size: Long, // use util Data
  arn: String) {

  def this(client: AmazonGlacierClient, desc: DescribeVaultOutput) =
    this(client, desc.getVaultName, desc.getCreationDate, desc.getLastInventoryDate,
        desc.getNumberOfArchives, desc.getSizeInBytes, desc.getVaultARN)

}
