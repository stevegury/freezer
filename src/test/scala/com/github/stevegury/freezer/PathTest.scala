package com.github.stevegury.freezer

import java.io.File
import org.scalatest.FunSuite

class PathTest extends FunSuite {
  test("relativize works") {
    val root = new File("/a/b/c")
    val f = new File("/a/b/c/d/e")
    assert(Path.relativize(root, f) === "d/e")
  }
}
