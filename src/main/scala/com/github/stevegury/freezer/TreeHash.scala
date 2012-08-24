package com.github.stevegury.freezer

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException


object TreeHash {
  type Hash = Array[Byte]
  val chunkSize = 1024 * 1024;

  def sha256(file: File): Hash = {
    val chunks = getChunksHashes(file)
    reduceTree(chunks)
  }

  private[this] def getChunksHashes(file: File): Seq[Hash] = {
    val md = MessageDigest.getInstance("SHA-256")
    val fileStream = new FileInputStream(file)
    val buffer = new Array[Byte](chunkSize)

    def hash(buf: Array[Byte], size: Int) = {
      md.reset()
      md.update(buf, 0, size)
      md.digest()
    }

    def read(res: List[Hash]): List[Hash] = {
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

  private[this] def reduceTree(hashes: Seq[Hash]): Hash = {
    val md = MessageDigest.getInstance("SHA-256")
    def concatHash(hs: Seq[Hash]): Hash = {
      md.reset()
      md.update(hs(0))
      if (hs.tail != Nil)
        md.update(hs(1))
      md.digest()
    }
    def reduce(hashes: Seq[Hash], res: List[Hash] = Nil): Seq[Hash] = {
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

  def toHex(h: Hash) = {
    h map { x =>
      Integer.toHexString(x & 0xFF)
    } map { c =>
      if (c.length == 1)
        "0" + c
      else
        c
    } reduceLeft(_ + _)
  }
}
