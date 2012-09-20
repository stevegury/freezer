package com.github.stevegury.freezer

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.glacier.AmazonGlacierClient
import com.amazonaws.services.glacier.model.{CreateVaultRequest, ListVaultsRequest}

import scala.collection.JavaConversions._

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
