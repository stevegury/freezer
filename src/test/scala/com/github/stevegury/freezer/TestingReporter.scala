package com.github.stevegury.freezer

import scala.collection.mutable.ArrayBuffer

class TestingReporter extends (String => Unit) {
  private[this] val buffer = new ArrayBuffer[String]

  def apply(msg: String): Unit = {
    buffer += msg
  }

  def clear(): Unit = buffer.clear()

  def getLastMessages: Seq[String] = buffer
}

class TestingStdinReader extends (String => String) {
  var questions = ArrayBuffer.empty[String]

  def apply(input: String) = input match {
    case _ =>
      questions += input
      ""
  }
}

object TestingStdinReader {
  def createFromInputs(inputs: String*) = {
    new (String => String) {
      var i = 0
      def apply(input: String) = input match {
        case _ =>
          val res = inputs(i)
          i += 1
          res
      }
    }
  }
}
