package com.github.stevegury.freezer

import java.io.{File, FileInputStream}
import java.security.MessageDigest

object TreeHash {
  val chunkSize = 1024 * 1024;

  def sha256(file: File): Array[Byte] = {
    val chunks = getChunksHashes(file)
    reduceTree(chunks)
  }

  def toHex(h: Array[Byte]) = {
    h map { x =>
      Integer.toHexString(x & 0xFF)
    } map { c =>
      if (c.length == 1)
        "0" + c
      else
        c
    } reduceLeft(_ + _)
  }

  private[this] def getChunksHashes(file: File): Seq[Array[Byte]] = {
    val md = MessageDigest.getInstance("SHA-256")
    val fileStream = new FileInputStream(file)
    val buffer = new Array[Byte](chunkSize)

    def hash(buf: Array[Byte], size: Int) = {
      md.reset()
      md.update(buf, 0, size)
      md.digest()
    }

    def read(res: List[Array[Byte]]): List[Array[Byte]] = {
      val bytesRead = fileStream.read(buffer, 0, chunkSize)
      if (bytesRead < 0)
        res.reverse
      else {
        val h = hash(buffer, bytesRead)
        read(h :: res)
      }
    }

    read(Nil)
  }

  private[this] def reduceTree(hashes: Seq[Array[Byte]]): Array[Byte] = {
    def concatHash(hs: Seq[Array[Byte]]): Array[Byte] = {
      val md = MessageDigest.getInstance("SHA-256")
      md.reset()
      md.update(hs(0))
      if (hs.tail != Nil)
        md.update(hs(1))
      md.digest()
    }
    def reduce(hashes: Seq[Array[Byte]], res: List[Array[Byte]] = Nil): Seq[Array[Byte]] = {
      if (hashes.isEmpty)
        res.reverse
      else
        reduce(hashes.drop(2), concatHash(hashes.take(2)) :: res)
    }
    if (hashes.size == 1)
      hashes.head
    else
      reduceTree(reduce(hashes))
  }
}
