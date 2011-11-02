package com.twitter.hashing

import java.nio.{ByteBuffer, ByteOrder}
import java.security.MessageDigest
import scala.collection.mutable

/**
 * Hashes a key into a 32-bit or 64-bit number (depending on the algorithm).
 *
 */
trait KeyHasher {
  def hashKey(key: Array[Byte]): Long
}

/**
 * Commonly used key hashing algorithms.
 */
object KeyHasher {
  private[this] val MaxUnsignedInt = 0xFFFFFFFFL
  /**
   * FNV fast hashing algorithm in 32 bits.
   * @see http://en.wikipedia.org/wiki/Fowler_Noll_Vo_hash
   */
  val FNV1_32 = new KeyHasher {
    def hashKey(key: Array[Byte]): Long = {
      val PRIME: Int = 16777619
      var i = 0
      val len = key.length
      var rv: Long = 0x811c9dc5L
      while (i < len) {
        rv = (rv * PRIME) ^ (key(i) & 0xff)
        i += 1
      }
      rv & MaxUnsignedInt
    }

    override def toString() = "FNV1_32"
  }

  /**
   * FNV fast hashing algorithm in 32 bits, variant with operations reversed.
   * @see http://en.wikipedia.org/wiki/Fowler_Noll_Vo_hash
   */
  val FNV1A_32 = new KeyHasher {
    def hashKey(key: Array[Byte]): Long = {
      val PRIME: Int = 16777619
      var i = 0
      val len = key.length
      var rv: Long = 0x811c9dc5L
      while (i < len) {
        rv = (rv ^ (key(i) & 0xff)) * PRIME
        i += 1
      }
      rv & MaxUnsignedInt
    }

    override def toString() = "FNV1A_32"
  }

  /**
   * FNV fast hashing algorithm in 64 bits.
   * @see http://en.wikipedia.org/wiki/Fowler_Noll_Vo_hash
   */
  val FNV1_64 = new KeyHasher {
    def hashKey(key: Array[Byte]): Long = {
      val PRIME: Long = 1099511628211L
      var i = 0
      val len = key.length
      var rv: Long = 0xcbf29ce484222325L
      while (i < len) {
        rv = (rv * PRIME) ^ (key(i) & 0xff)
        i += 1
      }
      rv
    }

    override def toString() = "FNV1_64"
  }

  /**
   * FNV fast hashing algorithm in 64 bits, variant with operations reversed.
   * @see http://en.wikipedia.org/wiki/Fowler_Noll_Vo_hash
   */
  val FNV1A_64 = new KeyHasher {
    def hashKey(key: Array[Byte]): Long = {
      val PRIME: Long = 1099511628211L
      var i = 0
      val len = key.length
      var rv: Long = 0xcbf29ce484222325L
      while (i < len) {
        rv = (rv ^ (key(i) & 0xff)) * PRIME
        i += 1
      }
      rv
    }

    override def toString() = "FNV1A_64"
  }

  /**
   * Ketama's default hash algorithm: the first 4 bytes of the MD5 as a little-endian int.
   * Wow, really? Who thought that was a good way to do it? :(
   */
  val KETAMA = new KeyHasher {
    def hashKey(key: Array[Byte]): Long = {
      val hasher = MessageDigest.getInstance("MD5")
      hasher.update(key)
      val buffer = ByteBuffer.wrap(hasher.digest)
      buffer.order(ByteOrder.LITTLE_ENDIAN)
      buffer.getInt.toLong & MaxUnsignedInt
    }

    override def toString() = "Ketama"
  }

  /**
   * The default memcache hash algorithm is the ITU-T variant of CRC-32.
   */
  val CRC32_ITU = new KeyHasher {
    def hashKey(key: Array[Byte]): Long = {
      var i = 0
      val len = key.length
      var rv: Long = MaxUnsignedInt
      while (i < len) {
        rv = rv ^ (key(i) & 0xff)
        var j = 0
        while (j < 8) {
          if ((rv & 1) != 0) {
            rv = (rv >> 1) ^ 0xedb88320L
          } else {
            rv >>= 1
          }
          j += 1
        }
        i += 1
      }
      (rv ^ MaxUnsignedInt) & MaxUnsignedInt
    }

    override def toString() = "CRC32_ITU"
  }

  /**
   * Paul Hsieh's hash function.
   * http://www.azillionmonkeys.com/qed/hash.html
   */
  val HSIEH = new KeyHasher {
    override def hashKey(key: Array[Byte]): Long = {
      var hash: Int = 0

      if (key.isEmpty)
        return 0

      for (i <- 0 until key.length / 4) {
        val b0 = key(i*4)
        val b1 = key(i*4 + 1)
        val b2 = key(i*4 + 2)
        val b3 = key(i*4 + 3)
        val s0 = (b1 << 8) | b0
        val s1 = (b3 << 8) | b2

        hash += s0
        val tmp = (s1 << 11) ^ hash
        hash = (hash << 16) ^ tmp
        hash += hash >>> 11
      }

      val rem = key.length % 4
      val offset = key.length - rem
      rem match {
        case 3 =>
          val b0 = key(offset)
          val b1 = key(offset + 1)
          val b2 = key(offset + 2)
          val s0 = b1 << 8 | b0
          hash += s0
          hash ^= hash << 16
          hash ^= b2 << 18
          hash += hash >>> 11
        case 2 =>
          val b0 = key(offset)
          val b1 = key(offset + 1)
          val s0 = b1 << 8 | b0
          hash += s0
          hash ^= hash << 11
          hash += hash >>> 17
        case 1 =>
          val b0 = key(offset)
          hash += b0
          hash ^= hash << 10
          hash += hash >>> 1
        case 0 => ()
      }

      hash ^= hash << 3
      hash += hash >>> 5
      hash ^= hash << 4
      hash += hash >>> 17
      hash ^= hash << 25
      hash += hash >>> 6

      hash & MaxUnsignedInt
    }

    override def toString() = "Hsieh"
  }

  /**
  * Jenkins Hash Function
  * http://en.wikipedia.org/wiki/Jenkins_hash_function
  */
  val JENKINS = new KeyHasher {
    override def hashKey(key: Array[Byte]): Long = {
      var a, b, c = 0xdeadbeef + key.size

      def rot(x: Int, k: Int) = (((x) << (k)) | ((x) >> (32 - (k))))

      def mix() {
        a -= c; a ^= rot(c, 4); c += b
        b -= a; b ^= rot(a, 6); a += c
        c -= b; c ^= rot(b, 8); b += a
        a -= c; a ^= rot(c, 16); c += b
        b -= a; b ^= rot(a, 19); a += c
        c -= b; c ^= rot(b, 4); b += a
      }

      def fin() {
        c ^= b; c -= rot(b, 14); a ^= c; a -= rot(c, 11)
        b ^= a; b -= rot(a, 25); c ^= b; c -= rot(b, 16)
        a ^= c; a -= rot(c, 4); b ^= a; b -= rot(a, 14)
        c ^= b; c -= rot(b, 24)
      }

      var block = 0
      val numBlocks = (key.size - 1) / 12
      while (block < numBlocks) {
        val offset = block * 12
        a += key(offset)
        a += key(offset + 1) << 8
        a += key(offset + 2) << 16
        a += key(offset + 3) << 24

        b += key(offset + 4)
        b += key(offset + 5) << 8
        b += key(offset + 6) << 16
        b += key(offset + 7) << 24

        c += key(offset + 8)
        c += key(offset + 9) << 8
        c += key(offset + 10) << 16
        c += key(offset + 11) << 24

        mix()
        block += 1
      }

      val remaining = key.size - (numBlocks * 12)
      val offset = numBlocks * 12

      if (remaining > 0) a += key(offset)
      if (remaining > 1) a += key(offset + 1) << 8
      if (remaining > 2) a += key(offset + 2) << 16
      if (remaining > 3) a += key(offset + 3) << 24

      if (remaining > 4) b += key(offset + 4)
      if (remaining > 5) b += key(offset + 5) << 8
      if (remaining > 6) b += key(offset + 6) << 16
      if (remaining > 7) b += key(offset + 7) << 24

      if (remaining > 8) c += key(offset + 8)
      if (remaining > 9) c += key(offset + 9) << 8
      if (remaining > 10) c += key(offset + 10) << 16
      if (remaining > 11) c += key(offset + 11) << 24

      if (key.size > 0) fin()

      (b.toLong << 32) + c.toLong
    }
  }

  /**
   * Return one of the key hashing algorithms by name. This is used to configure a memcache
   * client from a config file.
   */
  def byName(name: String): KeyHasher = {
    name match {
      case "fnv" => FNV1_32
      case "fnv1" => FNV1_32
      case "fnv1-32" => FNV1_32
      case "fnv1a-32" => FNV1A_32
      case "fnv1-64" => FNV1_64
      case "fnv1a-64" => FNV1A_64
      case "ketama" => KETAMA
      case "crc32-itu" => CRC32_ITU
      case "hsieh" => HSIEH
      case _ => throw new NoSuchElementException(name)
    }
  }
}
