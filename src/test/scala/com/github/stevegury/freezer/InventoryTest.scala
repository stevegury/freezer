package com.github.stevegury.freezer

import java.io.File

import com.github.stevegury.freezer.tasks.{Backup, Inventory, Init}
import org.scalatest.{BeforeAndAfter, FunSuite}

class InventoryTest extends FunSuite with BeforeAndAfter with DirSetup {

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

  test("Call Inventory does the right thing on an empty vault") {
    val vault = new TestingVault
    val inv = new Inventory(vault, reporter)

    // first call generate a inventory-retrieval-job
    assert(inv.run() === 0)
    assert(reporter.getLastMessages.size == 1)
    assert(reporter.getLastMessages.head.startsWith("Inventory in progress"))
    reporter.clear()

    // second call retrieve the inventory
    assert(inv.run() === 0)
    assert(reporter.getLastMessages.isEmpty)
  }

  test("Call Inventory does the right thing on a non-empty vault") {
    val f1 = new File(tmpDir, "f1")
    val f2 = new File(tmpDir, "d1/f2")
    val f3 = new File(tmpDir, "d1/d2/f3")
    Seq(f1, f2, f3) foreach { touch }

    val vault = new TestingVault
    val inv = new Inventory(vault, reporter)
    val backup = new Backup(tmpDir, cfg, vault, reporter)

    assert(backup.run() === 0)
    reporter.clear()

    // first call generate a inventory-retrieval-job
    assert(inv.run() === 0)
    reporter.clear()

    // second call retrieve the inventory
    assert(inv.run() === 0)
    assert(reporter.getLastMessages.size === 3)
    assert(reporter.getLastMessages.toSet === Set("f1", "d1/f2", "d1/d2/f3"))

    f3.delete()
    assert(backup.run() === 0)
    reporter.clear()

    vault.deleteAllJobs()
    // first call generate a inventory-retrieval-job
    assert(inv.run() === 0)
    reporter.clear()
    assert(inv.run() === 0)

    assert(reporter.getLastMessages.size === 2)
    assert(reporter.getLastMessages.toSet === Set("f1", "d1/f2"))
  }
}
