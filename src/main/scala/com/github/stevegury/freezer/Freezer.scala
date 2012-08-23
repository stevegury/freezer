package com.github.stevegury.freezer

import com.amazonaws.auth.{AWSCredentials, PropertiesCredentials}
import com.amazonaws.services.glacier.AmazonGlacierClient

import java.io.File


object Freezer {
  def main(args: Array[String]) = {
    println("Freezer")
  }

  private[freezer] val defaultCredentialsFilename =
    new File(System.getProperty("user.home"), ".aws.glacier.credentials")
}

class GlacierClient(credentials: AWSCredentials) {
  def this() = this(new PropertiesCredentials(Freezer.defaultCredentialsFilename))

  /*private[this]*/ val client = new AmazonGlacierClient(credentials)

  def listVault: List[Vault]
}

object GlacierClient extends GlacierClient()

class Vault(
  name: String,
  creationDate: String,
  inventoryDate: String,
  numberOfArchive: Long,
  size: Long, // use util Data
  arn: String) {

}
