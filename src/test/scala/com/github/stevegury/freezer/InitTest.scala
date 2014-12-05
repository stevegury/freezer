package com.github.stevegury.freezer

import java.io.File
import java.net.MalformedURLException

import com.github.stevegury.freezer.tasks.Init
import org.scalatest.{BeforeAndAfter, FunSuite}

class InitTest extends FunSuite with BeforeAndAfter with DirSetup {

  before {
    initDir()
  }

  after {
    destroyDir()
  }

  test("Init ask for the right things") {
    val init = new Init(tmpDir, new TestingReporter, new TestingStdinReader)
    val cfg = init.initConfig()

    assert(cfg.vaultName === tmpDir.getName)
  }

  test("Init throw on invalid credentials format") {
    val emptyCreds = File.createTempFile("emptyCreds", System.nanoTime.toString)
    val stdinReader = TestingStdinReader.createFromInputs("", emptyCreds.getAbsolutePath, "", "")

    val init = new Init(tmpDir, new TestingReporter, stdinReader)
    intercept[IllegalArgumentException] {
      init.initConfig()
    }
  }

  test("Init throw on invalid endpoint") {
    val stdinReader = TestingStdinReader.createFromInputs("", "", "htpp://blabla asdkbjasd", "")

    val init = new Init(tmpDir, new TestingReporter, stdinReader)
    intercept[MalformedURLException] {
      init.initConfig()
    }
  }
}
