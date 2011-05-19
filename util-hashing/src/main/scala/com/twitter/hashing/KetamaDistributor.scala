package com.twitter.hashing

import java.nio.{ByteBuffer, ByteOrder}
import java.security.MessageDigest
import java.util.TreeMap
import scala.collection.JavaConversions._

case class KetamaNode[A](identifier: String, weight: Int, handle: A)

class KetamaDistributor[A](nodes: Seq[KetamaNode[A]], numReps: Int, hasher: KeyHasher) extends Distributor[A] {
  private val continuum = {
    val continuum = new TreeMap[Long, KetamaNode[A]]()
    val nodeCount = nodes.size
    val totalWeight = nodes.foldLeft(0) { _ + _.weight }.toDouble

    nodes foreach { node =>
      val percent = node.weight.toDouble / totalWeight
      // the tiny fudge fraction is added to counteract float errors.
      val pointsOnRing = (percent * nodeCount * (numReps / 4) + 0.0000000001).toInt
      for (i <- 0 until pointsOnRing) {
        val key = node.identifier + "-" + i
        for (k <- 0 until 4) {
          continuum += computeHash(key, k) -> node
        }
      }
    }

    assert(continuum.size <= numReps * nodeCount)
    assert(continuum.size >= numReps * (nodeCount - 1))

    continuum
  }


  def nodeForKey(key: String) = {
    val hash = hasher.hashKey(key.getBytes("utf-8"))
    val entry = continuum.ceilingEntry(hash)
    val node = Option(entry).getOrElse(continuum.firstEntry).getValue
    node.handle
  }

  def nodeCount = nodes.size

  protected def computeHash(key: String, alignment: Int) = {
    val hasher = MessageDigest.getInstance("MD5")
    hasher.update(key.getBytes("utf-8"))
    val buffer = ByteBuffer.wrap(hasher.digest)
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    buffer.position(alignment << 2)
    buffer.getInt.toLong & 0xffffffffL
  }
}


