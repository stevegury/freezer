package com.github.stevegury.freezer

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.glacier.AmazonGlacierClient
import com.amazonaws.services.glacier.model.{DescribeVaultRequest, CreateVaultRequest, DeleteVaultRequest, ListVaultsRequest}
import com.twitter.util.StorageUnit

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer

class GlacierClient(val credentials: AWSCredentials, val endpoint: String) {
  /*private[this]*/ val client = {
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
      buf ++= res.getVaultList map { desc => new Vault(client, credentials, desc) }
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
      val size: StorageUnit = new StorageUnit(desc.getSizeInBytes)
      val arn = desc.getVaultARN

      Some(new Vault(client, credentials, name, creationDate, inventoryDate, numberOfArchive, size, arn))
    } catch {
      case ex: Throwable =>
        println(ex)
        None
    }
  }
}

object GlacierClient extends GlacierClient(defaultCredentials, defaultEndpoint)
