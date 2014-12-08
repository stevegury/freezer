package com.github.stevegury.freezer

import java.io.File

import com.github.stevegury.freezer.tasks.{Restore, Inventory, Init, Backup}
import org.scalatest.{BeforeAndAfter, FunSuite}

class RestoreTest extends FunSuite with BeforeAndAfter with DirSetup {

  var reporter: TestingReporter = null
  var cfg: Config = null

  before {
    initDir()
    reporter = new TestingReporter
    cfg = Init.initConfig(tmpDir, {str: String => ""})
  }

  after {
    destroyDir()
  }

  test("Restore a vault without error") {
    val f1 = new File(tmpDir, "f1")
    val f2 = new File(tmpDir, "d1/f2")
    val f3 = new File(tmpDir, "d1/d2/f3")
    Seq(f1, f2, f3) foreach { touch }

    val vault = new TestingVault
    val inv = new Inventory(vault, reporter)
    val backup = new Backup(tmpDir, cfg, vault, reporter)

    assert(backup.run() === 0)
    reporter.clear()

    val tmp2 = File.createTempFile("freezer", System.nanoTime.toString.substring(4))
    tmp2.delete()
    tmp2.mkdir()

    val restore = new Restore(tmp2, vault, reporter)
    assert(restore.run() === 0)
    assert(reporter.getLastMessages.head.startsWith("Inventory in progress"))
    reporter.clear()
    assert(restore.run() === 0)
    assert(reporter.getLastMessages.toSet ===
      Set("f1", "d1/f2", "d1/d2/f3").map(f => s"Requesting download for: '$f'"))
    reporter.clear()
    assert(restore.run() === 0)
    assert(reporter.getLastMessages.toSet ===
      Set("f1", "d1/f2", "d1/d2/f3").map(f => s"Downloading '$f'..."))

    // TODO: check that the content of the files are identical
  }
}
