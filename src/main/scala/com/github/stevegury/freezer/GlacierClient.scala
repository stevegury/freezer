package com.github.stevegury.freezer

import com.amazonaws.auth.{PropertiesCredentials, AWSCredentials}
import com.amazonaws.services.glacier.AmazonGlacierClient
import com.amazonaws.services.glacier.model.{DescribeVaultRequest, CreateVaultRequest, ListVaultsRequest}
import java.io.File
import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer

trait GlacierClient {
  def listVaults: Seq[Vault]
  def createVault(name: String): Vault
  def getVault(name: String): Option[Vault]
}

class AwsGlacierClient(val credentials: AWSCredentials, val endpoint: String) extends GlacierClient {

  def this(cfg: Config) = this(new PropertiesCredentials(new File(cfg.credentials)), cfg.endpoint)

  private[this] val client = {
    val c = new AmazonGlacierClient(credentials)
    c.setEndpoint(endpoint)
    c
  }

  /**
   * List the vaults
   */
  def listVaults: Seq[Vault] = {
    val buf = new ArrayBuffer[Vault]
    val req = new ListVaultsRequest
    var res = client.listVaults(req)
    while (res.getMarker != null) {
      buf ++= res.getVaultList map { desc => new AwsVault(client, credentials, desc) }
      req.setMarker(res.getMarker)
      res = client.listVaults(req)
    }
    buf.toSeq
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
  def getVault(name: String): Option[Vault] = {
    try {
      val desc = client.describeVault(new DescribeVaultRequest(name))
      val creationDate = desc.getCreationDate
      val inventoryDate = desc.getLastInventoryDate
      val numberOfArchive = desc.getNumberOfArchives
      val size = desc.getSizeInBytes
      val arn = desc.getVaultARN

      Some(new AwsVault(client, credentials, name, creationDate, inventoryDate, numberOfArchive, size, arn))
    } catch {
      case ex: Throwable =>
        None
    }
  }
}
