package com.twitter.hashing

import java.security.MessageDigest

case class KetamaNode[A](identifier: String, weight: Int, handle: A)

/**
 * @note Certain versions of libmemcached return subtly different results for points on
 * ring. In order to always hash a key to the same server as the
 * clients who depend on those versions of libmemcached, we have to reproduce their result.
 * If the `oldLibMemcachedVersionComplianceMode` is `true` the behavior will be reproduced.
 */
class KetamaDistributor[A](
  ketamaNodes: Seq[KetamaNode[A]],
  numReps: Int,
  oldLibMemcachedVersionComplianceMode: Boolean = false)
    extends Distributor[A] {

  /**
   * Extract a little-endian integer from a given byte array starting at `offset`.
   */
  @inline private[hashing] def byteArrayToLE(bytes: Array[Byte], offset: Int): Int =
    bytes(3 + offset) << 24 |
      (bytes(2 + offset) & 0xff) << 16 |
      (bytes(1 + offset) & 0xff) << 8 |
      bytes(0 + offset) & 0xff

  /**
   * Feeds a given integer `i` into a [[MessageDigest]] in the way close to
   *
   * {{{
   *   md.update(i.toString.getBytes)
   * }}}
   *
   * But without allocations: no to-string, no to-byte-array conversions.
   */
  @inline private[hashing] def hashInt(i: Int, md: MessageDigest): Unit = {
    var j = i
    var div = Math.pow(10, Math.log10(i).toInt).toInt
    // We're moving from left to right.
    // For example, 123 should result in 49 ("1"), 50 ("2"), 51 ("3") bytes respectfully.
    while (j > 9 || div >= 10) {
      val d = j / div
      if (d != 0) {
        md.update(('0' + d).toByte)
        j = j % div
      } else if (j != i) {
        md.update('0'.toByte)
      }
      div = div / 10
    }

    md.update(('0' + j).toByte)
  }

  private[this] val continuum: java.util.TreeMap[Long, KetamaNode[A]] = {
    val md5 = MessageDigest.getInstance("MD5")
    val underlying = new java.util.TreeMap[Long, KetamaNode[A]]()
    val dash: Byte = '-'.toByte

    val nodeCount = ketamaNodes.size
    val totalWeight = ketamaNodes.foldLeft(0) { _ + _.weight }

    val it = ketamaNodes.iterator
    while (it.hasNext) {
      val node = it.next()

      val pointsOnRing = if (oldLibMemcachedVersionComplianceMode) {
        val percent = node.weight.toFloat / totalWeight.toFloat
        (percent * numReps / 4 * nodeCount.toFloat + 0.0000000001).toInt
      } else {
        val percent = node.weight.toDouble / totalWeight.toDouble
        (percent * nodeCount * (numReps / 4) + 0.0000000001).toInt
      }

      val prefix = node.identifier.getBytes("UTF-8")

      var i = 0
      while (i < pointsOnRing) {
        md5.update(prefix)
        md5.update(dash)

        hashInt(i, md5)

        val buffer = md5.digest()

        underlying.put(byteArrayToLE(buffer, 0).toLong & 0xffffffffL, node)
        underlying.put(byteArrayToLE(buffer, 4).toLong & 0xffffffffL, node)
        underlying.put(byteArrayToLE(buffer, 8).toLong & 0xffffffffL, node)
        underlying.put(byteArrayToLE(buffer, 12).toLong & 0xffffffffL, node)

        i += 1
      }
    }

    if (!oldLibMemcachedVersionComplianceMode) {
      assert(underlying.size <= numReps * nodeCount)
      assert(underlying.size >= numReps * (nodeCount - 1))
    }

    underlying
  }

  def nodes: Seq[A] = ketamaNodes.map(_.handle)
  def nodeCount: Int = ketamaNodes.size

  // hashes are 32-bit because they are 32-bit on the libmemcached and
  // we need to maintain compatibility with libmemcached
  private[this] def truncateHash(hash: Long): Long = hash & 0xffffffffL

  private def mapEntryForHash(hash: Long): java.util.Map.Entry[Long, KetamaNode[A]] = {
    val truncatedHash = truncateHash(hash)
    val entry = continuum.ceilingEntry(truncatedHash)
    if (entry == null) continuum.firstEntry else entry
  }

  def partitionIdForHash(hash: Long): Long =
    mapEntryForHash(hash).getKey

  def entryForHash(hash: Long): (Long, A) = {
    val entry = mapEntryForHash(hash)
    (entry.getKey, entry.getValue.handle)
  }

  def nodeForHash(hash: Long): A =
    mapEntryForHash(hash).getValue.handle
}
