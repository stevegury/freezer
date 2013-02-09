package com.github.stevegury.freezer

import java.io.File
import scala.util.matching.Regex
import com.amazonaws.services.glacier.TreeHashGenerator._
import scala.Some

object Util {
  /**
   * Return the relative path between two files
   * ex: relativize(new File("/a/b/c"), new File("/a/b/c/d/e/f")) == "d/e/f"
   */
  def relativize(base: File, path: File): String = {
    def inCommon(a: List[String], b: List[String], res: List[String])
      : List[String] = (a, b) match {
        case (Nil, Nil) => res
        case (x :: xs, y :: ys) if x == y => inCommon(xs, ys, x :: res)
        case _ => res
      }

    val baseList = base.getAbsolutePath.split(File.separator).toList
    val pathList = path.getAbsolutePath.split(File.separator).toList
    val common = inCommon(baseList, pathList, Nil).size
    "../" * (baseList.size - common) + pathList.drop(common).mkString("/")
  }

  /**
   * Filter a Seq of files based on a Seq of exclusions (regular expression)
   * WARNING: The regex must match the full (relativized) path
   * Ex: "^dir" will match File("dir") but not File("directory") nor File("subdir")
   */
  def filterFiles(
    files: Seq[File], exclusions: Seq[String], relativize: File => String
  ): Seq[File] = {
    files filter { file =>
      exclusions forall { excl =>
        val regex = new Regex(excl)
        val path = relativize(file)
        ! (regex.findFirstIn(path) == Some(path))
      }
    }
  }

  /**
   * Based on an index (relative path -> ArchiveInfo), find the modified files
   * Note: The ArchiveInfo structure contains a hash of the file which is used
   * if the file size differ
   */
  def updatedFiles0(
    file: File, index: Map[String, ArchiveInfo], relativize: File => String, updated: Seq[File]
  ): Seq[File] = {
    if (file.isDirectory)
      file.listFiles().foldLeft(updated) {
        (updated, file) => updatedFiles0(file, index, relativize, updated) }
    else if (file.getName == configFilename) { /* skip */ updated }
    else if (file.length() == 0) { /* no supported WTF ??? */ updated }
    else {
      val path = relativize(file)
      def h = calculateTreeHash(file)

      index.get(path) match {
        case Some(info) if info.size != file.length() => updated :+ file
        case Some(info) if h == info.hash => updated
        case _ => updated :+ file
      }
    }
  }

  def fileListStream(root: File): Stream[File] = {
    def rec(files: List[File]): Stream[File] = files match {
      case f :: fs if f.isDirectory => rec(f.listFiles().toList ::: fs)
      case f :: fs if f.isFile =>      Stream.cons(f, rec(fs))
      case _ =>                        Stream.empty[File]
    }
    rec(root.listFiles().toList)
  }

  def updatedFiles(
    file: File, index: Map[String, ArchiveInfo], relativize: File => String
  ): Stream[File] = {
    fileListStream(file) filter {
      _.getName != configFilename
    } filter {
      _.length() != 0
    } filter { file =>
      val path = relativize(file)
      def h = calculateTreeHash(file)

      index.get(path) map { info =>
        info.size != file.length() || info.hash != h
      } getOrElse(false)
    }
  }
}
