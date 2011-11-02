package com.twitter.hashing

import java.nio.{ByteBuffer, ByteOrder}
import java.security.MessageDigest
import java.util.TreeMap
import scala.collection.JavaConversions._

case class KetamaNode[A](identifier: String, weight: Int, handle: A)

class KetamaDistributor[A](_nodes: Seq[KetamaNode[A]], numReps: Int) extends Distributor[A] {
  private val continuum = {
    val continuum = new TreeMap[Long, KetamaNode[A]]()
    val nodeCount = _nodes.size
    val totalWeight = _nodes.foldLeft(0) { _ + _.weight }.toDouble

    _nodes foreach { node =>
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

  def nodes = _nodes map { _.handle }
  def nodeCount = _nodes.size

  def nodeForHash(hash: Long) = {
    // hashes are 32-bit because they are 32-bit on the libmemcached and
    // we need to maintain compatibility with libmemcached
    val trunucatedHash = hash & 0xffffffffL

    val entry = continuum.ceilingEntry(trunucatedHash)
    val node = Option(entry).getOrElse(continuum.firstEntry).getValue
    node.handle
  }

  protected def computeHash(key: String, alignment: Int) = {
    val hasher = MessageDigest.getInstance("MD5")
    hasher.update(key.getBytes("utf-8"))
    val buffer = ByteBuffer.wrap(hasher.digest)
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    buffer.position(alignment << 2)
    buffer.getInt.toLong & 0xffffffffL
  }
}


