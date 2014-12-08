package com.github.stevegury.freezer

import java.io.{FileOutputStream, File}

import com.github.stevegury.freezer.tasks.{Init, Backup}
import org.scalatest.{BeforeAndAfter, FunSuite}

class BackupTest extends FunSuite with BeforeAndAfter with DirSetup {

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

  test("Backup on empty directory does nothing") {
    val vault = new TestingVault
    val backup = new Backup(tmpDir, tmpDir, cfg, vault, reporter)

    assert(backup.run() === 0)
    assert(vault.getContentPath.isEmpty)
    assert(reporter.getLastMessages.head === "Everything up-to-date.")
  }

  test("Backup on non-empty directory does the right thing") {
    val f1 = new File(tmpDir, "f1")
    val f2 = new File(tmpDir, "d1/f2")
    val f3 = new File(tmpDir, "d1/d2/f3")
    Seq(f1, f2, f3) foreach { touch }

    val vault = new TestingVault
    val backup = new Backup(tmpDir, tmpDir, cfg, vault, reporter)

    {
      assert(backup.run() === 0)
      val paths = vault.getContentPath.toSet
      val expectedPaths = Set("f1", "d1/f2", "d1/d2/f3")
      assert(expectedPaths === paths)
      val msg = reporter.getLastMessages.toSet
      val expectedMsg = expectedPaths.map(p => s"Uploading new file: $p")
      assert(expectedMsg === msg)
      reporter.clear()
    }

    touch(f3)

    {
      assert(backup.run() === 0)
      val paths = vault.getContentPath.toSet
      val expectedPaths = Set("f1", "d1/f2", "d1/d2/f3")
      assert(expectedPaths === paths)
      val msg = reporter.getLastMessages.toSet
      val expectedMsg = Set(s"Updating modified file: d1/d2/f3")
      assert(expectedMsg === msg)
      reporter.clear()
    }

    f2.delete()

    {
      assert(backup.run() === 0)
      val paths = vault.getContentPath.toSet
      val expectedPaths = Set("f1", "d1/d2/f3")
      assert(expectedPaths === paths)
      val msg = reporter.getLastMessages.toSet
      val expectedMsg = Set(s"Removing deleted file: d1/f2")
      assert(expectedMsg === msg)
      reporter.clear()
    }

    {
      val out = new FileOutputStream(f3)
      out.write(Array.empty[Byte])
      out.close()

      assert(backup.run() === 0)
      val paths = vault.getContentPath.toSet
      val expectedPaths = Set("f1")
      assert(expectedPaths === paths)
      val msg = reporter.getLastMessages.toSet
      val expectedMsg = Set(s"Removing zero-byte file: d1/d2/f3")
      assert(expectedMsg === msg)
      reporter.clear()
    }
  }

  test("Backup respect regex exclusions") {
    val f1 = new File(tmpDir, ".DS_Store")
    val f2 = new File(tmpDir, "to_be_excluded")
    val f3 = new File(tmpDir, "subdir/subdir2/file")
    Seq(f1, f2, f3) foreach { touch }

    val stdinReader = TestingStdinReader.createFromInputs("", "", "", "\\.DS_Store")
    cfg = Init.initConfig(tmpDir, stdinReader)
    val vault = new TestingVault
    val backup = new Backup(tmpDir, tmpDir, cfg, vault, reporter)

    // upload everything
    {
      assert(backup.run() === 0)
      val paths = vault.getContentPath.toSet
      val expectedPaths = Set("to_be_excluded", "subdir/subdir2/file")
      assert(expectedPaths === paths)
      reporter.clear()
    }

    {
      val stdinReader = TestingStdinReader.createFromInputs("", "", "", "\\.DS_Store,to_be_excluded")
      cfg = Init.initConfig(tmpDir, stdinReader)
      val backup = new Backup(tmpDir, tmpDir, cfg, vault, reporter) // to reload the cfg
      assert(backup.run() === 0)

      val paths = vault.getContentPath.toSet
      val expectedPaths = Set("subdir/subdir2/file")
      assert(expectedPaths === paths)
      val msg = reporter.getLastMessages.toSet
      val expectedMsg = Set("Removing deleted file: to_be_excluded")
      assert(expectedMsg === msg)
      reporter.clear()
    }

    {
      val stdinReader = TestingStdinReader.createFromInputs("", "", "", "other")
      cfg = Init.initConfig(tmpDir, stdinReader)
      val backup = new Backup(tmpDir, tmpDir, cfg, vault, reporter) // to reload the cfg
      assert(backup.run() === 0)

      val paths = vault.getContentPath.toSet
      val expectedPaths = Set(".DS_Store", "to_be_excluded", "subdir/subdir2/file")
      assert(expectedPaths === paths)
      val msg = reporter.getLastMessages.toSet
      val expectedMsg = Set("Uploading new file: .DS_Store", "Uploading new file: to_be_excluded")
      assert(expectedMsg === msg)
      reporter.clear()
    }

    {
      val stdinReader = TestingStdinReader.createFromInputs("", "", "", "subdir")
      cfg = Init.initConfig(tmpDir, stdinReader)
      val backup = new Backup(tmpDir, tmpDir, cfg, vault, reporter) // to reload the cfg
      assert(backup.run() === 0)

      val paths = vault.getContentPath.toSet
      val expectedPaths = Set(".DS_Store", "to_be_excluded")
      assert(expectedPaths === paths)
      val msg = reporter.getLastMessages.toSet
      val expectedMsg = Set("Removing deleted file: subdir/subdir2/file")
      assert(expectedMsg === msg)
      reporter.clear()
    }
  }
}
