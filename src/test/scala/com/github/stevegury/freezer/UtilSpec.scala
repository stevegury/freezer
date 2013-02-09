package com.github.stevegury.freezer

import com.twitter.util.Time
import com.amazonaws.services.glacier.TreeHashGenerator._
import java.io._
import org.specs2._
import org.specs2.mock._
import scala.util.Random

class UtilSpec extends Specification { def is =

  "detect file change"                     ! fileChange().detect ^
  "relativize path works"                  ! relativize().test   ^
  "filter files based on regex exclusions" ! filterFiles().test


  case class relativize() {
    def test = {
      val root = new File("/a/b/c")
      val f = new File("/a/b/c/d/e")
      Util.relativize(root, f) must beEqualTo("d/e")
    }
  }

  case class filterFiles() {
    def test = {
      val files = Seq(
        new File("/root/a.html"),
        new File("/root/subdir/b.txt"),
        new File("/root/subdir/Toto.java"),
        new File("/root/subdir/Toto.class"),
        new File("/root/dir/a.txt")
      )
      val exclusions = Seq(".*\\.class$", "^dir.*", "sub")
      val res = Util.filterFiles(files, exclusions, Util.relativize(new File("/root"), _))
      val expected = Set(
        new File("/root/a.html"),
        new File("/root/subdir/b.txt"),
        new File("/root/subdir/Toto.java")
      )
      res.toSet must beEqualTo(expected)
    }
  }

  case class fileChange() extends Mockito {
    lazy val root = File.createTempFile("root", System.nanoTime.toString)
    delete(root)
    root.mkdir()
    val rnd = new Random

    def delete(f: File) {
      if (f.isDirectory)
        f.listFiles.foreach(delete)
      else
        f.delete()
    }

    def createDummy(dir: File, name: String) = {
      val f = new File(root, name)
      f.createNewFile()
      val fos = new FileOutputStream(f)
      (1 to rnd.nextInt(4096)) foreach { i => fos.write(rnd.nextInt()) }
      f
    }

    def initDir() = {
      val a = createDummy(root, "a")
      val b = createDummy(root, "b")
      val subdir = {
        val d = new File(root, "subdir")
        d.mkdir()
        d
      }
      val c = createDummy(subdir, "c")
      Seq(a,b,c)
    }

    def createIndex(files: Seq[File]) = files map { f =>
      val path = Util.relativize(root, f)
      path -> ArchiveInfo(
        rnd.nextInt().toString,
        path,
        Time.now.toString(),
        f.length(),
        calculateTreeHash(f)
      )
    } toMap

    def detect = {
      val files = initDir()
      val index = createIndex(files)
      val modified = Util.updatedFiles(
        root, index, Util.relativize(root, _))
      modified must beEmpty

      val newFiles = initDir()
      val newModified = Util.updatedFiles(
        root, index, Util.relativize(root, _))
      newModified.toSet must beEqualTo(newFiles)
      val newIndex = createIndex(newFiles)

      val newB = createDummy(root, "b")
      val newModified2 = Util.updatedFiles(
        root, newIndex, Util.relativize(root, _))
      newModified2.toSet must beEqualTo(Set(newB))

      delete(root)
      newModified2.toSet must beEqualTo(Set(newB))
    }
  }
}
