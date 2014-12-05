package com.github.stevegury.freezer.tasks

import java.net.URL

import com.amazonaws.auth.PropertiesCredentials
import com.github.stevegury.freezer._
import java.io.File
import scala.io.StdIn

class Init(root: File, reporter: String => Unit, stdinReader: String => String) {
  def run(): Int = {
    var cfg = initConfig()
    var isVaultAvailable = false
    while (!isVaultAvailable) {
      val client = new AwsGlacierClient(cfg)
      val vault = client.getVault(cfg.vaultName)
      if (! vault.isDefined)
        isVaultAvailable = true
      else {
        reporter(s"Vault '${cfg.vaultName}' already exists! Try again")
        cfg = initConfig()
      }
    }
    0
  }

  def initConfig(): Config = {
    statusDir(root).mkdirs()
    val vaultName = readFromStdin("VaultName", root.getName)
    val credentials = {
      val creds = readFromStdin("Credential location", defaultCredentialsFilename)
      new PropertiesCredentials(new File(creds)) // check validity
      creds
    }
    val endpoint = {
      val url = readFromStdin("Endpoint", defaultEndpoint)
      new URL(url) // check validity
      url
    }
    val exclusions = readFromStdin("Exclusions (comma separated)", ".*\\.DS_Store$").split(",").map(_.r)
    val cfg = Config(vaultName, credentials, endpoint, exclusions)
    cfg.save(new File(configDir(root), "config"))
    cfg
  }

  private[this] def readFromStdin(txt: String, default: String) = {
    val userInput = stdinReader(s"$txt [$default]: ")
    if ("" != userInput)
      userInput
    else
      default
  }
}
