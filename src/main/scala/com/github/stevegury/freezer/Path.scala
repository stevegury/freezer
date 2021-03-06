package com.github.stevegury.freezer

import java.io.File
import scala.util.matching.Regex
import com.amazonaws.services.glacier.TreeHashGenerator._
import scala.Some

object Path {
  /**
   * Return the relative path between two files
   * ex: relativize(new File("/a/b/c"), new File("/a/b/c/d/e/f")) == "d/e/f"
   */
  def relativize(base: File, path: File): String = {
    def inCommon(a: List[String], b: List[String], res: List[String]) : List[String] =
      (a, b) match {
        case (Nil, Nil) => res
        case (x :: xs, y :: ys) if x == y => inCommon(xs, ys, x :: res)
        case _ => res
      }

    val baseList = base.getAbsolutePath.split(File.separator).toList
    val pathList = path.getAbsolutePath.split(File.separator).toList
    val common = inCommon(baseList, pathList, Nil).size
    "../" * (baseList.size - common) + pathList.drop(common).mkString("/")
  }
}
