package com.github.stevegury.freezer

import java.io._
import org.specs2._
import org.specs2.mock._
import scala.util.Random

class FreezerSpec extends Specification { def is =

  "create a new config correctly"   ! freezer().createConfig

  case class freezer() extends Mockito with specification.BeforeAfter {
    lazy val root = File.createTempFile("root", System.nanoTime.toString)
    val rnd = new Random

    def before = {
      delete(root)
      root.mkdir()
    }
    def after = {
      println(root)
      //delete(root)
    }

    def delete(f: File) {
      if (f.isDirectory)
        f.listFiles.foreach(delete)
      else
        f.delete()
    }

    def createDummy(dir: File, name: String) {
      val f = new File(root, name)
      f.createNewFile()
      println("creating " + f)
      val fos = new FileOutputStream(f)
      (1 to rnd.nextInt(4096)) foreach { i => fos.write(rnd.nextInt()) }
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

    def createConfig = this {
      initDir()

//      val vault = mock[Vault]
//      vault.upload() returns ArchiveInfo()
//      val client = mock[GlacierClient]
//      client.createVault(anyString) returns vault

      val cfg = Freezer.createConfig(root)
      cfg.vaultName must beEqualTo(root.getName)
      cfg.archiveInfos must beEmpty

//      def index(ais: Seq[ArchiveInfo]) = ais map { info => (info.desc -> info) } toMap
//      // Scan modified files
//      val updated = Freezer.updatedFiles(root, index(cfg.archiveInfos), root, Seq.empty[File])
//
//      updated.map(_.getName).toSet mustEqual Set("a", "b", "subdir/c")
//
//      // update one file
//      createDummy(root, "a")
//      val cfg3 = Freezer.backup(dir, cfg2, client)
//      val hashesAfter = cfg3.archiveInfos map { info => (info.desc -> info.hash) } toMap
//
//      (hashesBefore - "a").values.toSet must beEqualTo ((hashesAfter - "a").values.toSet)
//      hashesBefore("a") must not be equalTo(hashesAfter("a"))
    }
  }
}
