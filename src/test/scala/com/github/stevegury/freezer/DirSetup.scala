package com.github.stevegury.freezer

import java.io.{FileOutputStream, File}

import scala.util.Random

trait DirSetup {
  private[this] val rng = new Random()

  var tmpDir: File = null

  protected def initDir(): Unit = {
    val tmp = File.createTempFile("freezer", System.nanoTime.toString.substring(4))
    tmp.delete()
    tmp.mkdir()
    tmpDir = tmp
  }

  protected def destroyDir(): Unit = {
    def deleteDirRec(dir: File): Unit = {
      val files = dir.listFiles()
      val (fs, ds) = files.partition(_.isFile)
      fs foreach { _.delete }
      ds foreach { d =>
        deleteDirRec(d)
        d.delete()
      }
    }
    deleteDirRec(tmpDir)
  }

  protected def touch(file: File): Unit = {
    file.getParentFile.mkdirs()
    val out = new FileOutputStream(file)
    val buffer = new Array[Byte](128)
    rng.nextBytes(buffer)
    out.write(buffer)
    out.close()
  }
}
