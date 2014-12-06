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

  test("Init doesn't ask anything if info provided") {
    val stdinReader = new TestingStdinReader
    val init = new Init(tmpDir, new TestingReporter, stdinReader)
    val fakeCreds = createFakeCredentials(File.createTempFile("fake-creds", System.currentTimeMillis().toString))
    val credPath = fakeCreds.getAbsolutePath
    val cfg = Init.initConfig(
      tmpDir,
      stdinReader,
      Some("myName"),
      Some(credPath),
      Some("https://glacier.toto.com"),
      Some("""target,\.git.*""")
    )

    assert(stdinReader.questions.size === 0)
    assert(cfg.vaultName === "myName")
    assert(cfg.credentials === credPath)
    assert(cfg.endpoint === "https://glacier.toto.com")
    assert(cfg.exclusions.map(_.regex) === Seq("target", "\\.git.*"))
  }

  test("Init ask for the right things") {
    val stdinReader = new TestingStdinReader
    val cfg = Init.initConfig(tmpDir, stdinReader)

    assert(stdinReader.questions.size === 4)
    assert(cfg.vaultName === tmpDir.getName)
  }

  test("Init throw on invalid credentials format") {
    val emptyCreds = File.createTempFile("emptyCreds", System.nanoTime.toString)
    val stdinReader = TestingStdinReader.createFromInputs("", emptyCreds.getAbsolutePath, "", "")

    intercept[IllegalArgumentException] {
      Init.initConfig(tmpDir, stdinReader)
    }
  }

  test("Init throw on invalid endpoint") {
    val stdinReader = TestingStdinReader.createFromInputs("", "", "htpp://blabla asdkbjasd", "")

    intercept[MalformedURLException] {
      Init.initConfig(tmpDir, stdinReader)
    }
  }
}
