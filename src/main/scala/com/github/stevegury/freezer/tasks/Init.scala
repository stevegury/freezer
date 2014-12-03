package com.github.stevegury.freezer.tasks

import com.github.stevegury.freezer._
import java.io.File
import scala.io.StdIn

class Init(root: File) {
  def run(): Int = {
    // TODO: Handle vault name collision
    initConfig()
    0
  }

  def initConfig(): Config = {
    statusDir(root).mkdirs()
    val cfg = Config(
      vaultName = readFromStdin("VaultName", root.getName),
      credentials = readFromStdin("Credential location", defaultCredentialsFilename),
      endpoint = readFromStdin("Endpoint", defaultEndpoint),
      exclusions = readFromStdin("Exclusions (comma separated)", ".*\\.DS_Store$").split(",").map(_.r)
    )
    val cfgOutput = new File(configDir(root), "config")
    cfg.save(cfgOutput)
    cfg
  }

  private[this] def readFromStdin(txt: String, default: String) = {
    val userInput = StdIn.readLine(s"$txt [$default]: ")
    if ("" != userInput)
      userInput
    else
      default
  }
}
