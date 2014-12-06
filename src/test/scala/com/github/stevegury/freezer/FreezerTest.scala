package com.github.stevegury.freezer

import com.github.stevegury.freezer.Freezer.parseOptions
import org.scalatest.FunSuite

class FreezerTest extends FunSuite {
  test("simple parsing") {
    val (cmds, opts) = parseOptions(Array("blabla"))
    assert(cmds === "blabla" :: Nil)
  }

  test("one argument") {
    val (cmds, opts) = parseOptions(Array("blabla", "toto"))
    assert(cmds === "blabla" :: "toto" :: Nil)
  }


  test("simple options parsing") {
    val (cmds, opts) = parseOptions("blabla --opt1".split(" "))
    assert(cmds === "blabla" :: Nil)
    assert(opts.get("opt1") === Some(""))
  }

  test("simple options parsing 2") {
    val (cmds, opts) = parseOptions("blabla --opt1 val1 --opt2 --opt3".split(" "))
    assert(cmds === "blabla" :: Nil)
    assert(opts.get("opt1") === Some("val1"))
    assert(opts.get("opt2") === Some(""))
    assert(opts.get("opt3") === Some(""))
  }
}
