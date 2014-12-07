package com.github.stevegury.freezer.tasks

import java.net.URL

import com.amazonaws.auth.PropertiesCredentials
import com.github.stevegury.freezer._
import java.io.File

class Init(root: File, reporter: String => Unit, stdinReader: String => String) {
  def run(): Int = {
    statusDir(root).mkdirs()
    var cfg = Init.initConfig(root, stdinReader)

    var isVaultCreated = false
    while (!isVaultCreated) {
      val client = new AwsGlacierClient(cfg)
      val vault = client.getVault(cfg.vaultName)
      if (! vault.isDefined) {
        isVaultCreated = true
        client.createVault(cfg.vaultName)
      } else {
        reporter(s"Vault '${cfg.vaultName}' already exists! Try again")
        cfg = Init.initConfig(root, stdinReader)
      }
    }
    cfg.save(new File(configDir(root), "config"))
    0
  }
}

object Init {

  /**
   * Create a Config instance.
   * This function may ask user for information via the stdinReader function
   */
  def initConfig(
    currentDir: File,
    stdinReader: String => String,
    optName: Option[String] = None,
    optCreds: Option[String] = None,
    optEndpoint: Option[String] = None,
    optExclusions: Option[String] = None
  ): Config = {

    def readFromStdin(txt: String, default: String) = {
      val userInput = stdinReader(s"$txt [$default]: ")
      if ("" != userInput)
        userInput
      else
        default
    }

    val vaultName = optName getOrElse readFromStdin("VaultName", currentDir.getName)
    val credentials = optCreds getOrElse {
      val creds = readFromStdin("Credential location", defaultCredentialsFilename)
      new PropertiesCredentials(new File(creds)) // check validity
      creds
    }
    val endpoint = optEndpoint getOrElse {
      val url = readFromStdin("Endpoint", defaultEndpoint)
      new URL(url) // check validity
      url
    }
    val exclusions = (optExclusions getOrElse readFromStdin("Exclusions (comma separated)", ""))
      .split(",").filterNot(_ == "").map(_.r)

    Config(vaultName, credentials, endpoint, exclusions)
  }
}
