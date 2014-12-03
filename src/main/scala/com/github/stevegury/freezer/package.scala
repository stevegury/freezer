package com.github.stevegury

import java.io.File
import com.amazonaws.auth.PropertiesCredentials

package object freezer {
  val configFilename = ".freezer"
  val configDirname = ".freezer"

  val defaultCredentialsFilename =
    System.getProperty("user.home") + File.separator + ".aws.glacier.credentials"

  val defaultCredentials =
    new PropertiesCredentials(new File(defaultCredentialsFilename))

  val defaultEndpoint = "https://glacier.us-east-1.amazonaws.com/"

  val ex = new Exception("Not yet implemented")
  def ??? = throw ex

  def configDir(root: File) = new File(root.getAbsoluteFile, configDirname)

  def statusDir(root: File) = new File(configDir(root), "status")
}
