package com.github.stevegury

import java.io.File
import com.amazonaws.auth.PropertiesCredentials

package object freezer {
  val configFilename = ".freezer"

  val defaultCredentialsFilename =
    System.getProperty("user.home") + File.separator + ".aws.glacier.credentials"

  val defaultCredentials =
    new PropertiesCredentials(new File(defaultCredentialsFilename))

  val defaultEndpoint = "https://glacier.us-east-1.amazonaws.com/"

  val ex = new Exception("Not yet implemented")
  def ??? = throw ex
}
