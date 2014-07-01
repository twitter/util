package com.twitter.hashing

import java.nio.{ByteBuffer, ByteOrder}
import java.security.MessageDigest
import java.util.TreeMap

import scala.collection.JavaConversions._

case class KetamaNode[A](identifier: String, weight: Int, handle: A)

class KetamaDistributor[A](
  _nodes: Seq[KetamaNode[A]],
  numReps: Int,
  // Certain versions of libmemcached return subtly different results for points on
  // ring. In order to always hash a key to the same server as the
  // clients who depend on those versions of libmemcached, we have to reproduce their result.
  // If the oldLibMemcachedVersionComplianceMode is true the behavior will be reproduced.
  oldLibMemcachedVersionComplianceMode: Boolean = false
) extends Distributor[A] {
  private val continuum = {
    val continuum = new TreeMap[Long, KetamaNode[A]]()

    val nodeCount   = _nodes.size
    val totalWeight = _nodes.foldLeft(0) { _ + _.weight }

    _nodes foreach { node =>
      val pointsOnRing = if (oldLibMemcachedVersionComplianceMode) {
        val percent = node.weight.toFloat / totalWeight.toFloat
        (percent * numReps / 4 * nodeCount.toFloat + 0.0000000001).toInt
      } else {
        val percent = node.weight.toDouble / totalWeight.toDouble
        (percent * nodeCount * (numReps / 4) + 0.0000000001).toInt
      }

      for (i <- 0 until pointsOnRing) {
        val key = node.identifier + "-" + i
        for (k <- 0 until 4) {
          continuum += computeHash(key, k) -> node
        }
      }
    }

    if (!oldLibMemcachedVersionComplianceMode) {
      assert(continuum.size <= numReps * nodeCount)
      assert(continuum.size >= numReps * (nodeCount - 1))
    }

    continuum
  }

  def nodes = _nodes map { _.handle }
  def nodeCount = _nodes.size

  private def mapEntryForHash(hash: Long) = {
    // hashes are 32-bit because they are 32-bit on the libmemcached and
    // we need to maintain compatibility with libmemcached
    val truncatedHash = hash & 0xffffffffL

    Option(continuum.ceilingEntry(truncatedHash))
        .getOrElse(continuum.firstEntry)
  }

  def entryForHash(hash: Long) = {
    val entry = mapEntryForHash(hash)
    (entry.getKey, entry.getValue.handle)
  }

  def nodeForHash(hash: Long) = {
    mapEntryForHash(hash).getValue.handle
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


