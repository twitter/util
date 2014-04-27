package com.twitter.hashing

/**
 * Hashes a key into a 32-bit or 64-bit number (depending on the algorithm).
 */
@deprecated("Prefer Hashable[Array[Byte],Long]", "6.12.1")
trait KeyHasher {
  def hashKey(key: Array[Byte]): Long
}

/**
 * Commonly used key hashing algorithms.
 */
object KeyHasher {
  def fromHashableInt(hashable: Hashable[Array[Byte],Int]): KeyHasher = new KeyHasher {
    def hashKey(key: Array[Byte]) = hashable(key).toLong
    override def toString = hashable.toString
  }
  def fromHashableLong(hashable: Hashable[Array[Byte],Long]): KeyHasher = new KeyHasher {
    def hashKey(key: Array[Byte]) = hashable(key)
    override def toString = hashable.toString
  }

  val FNV1_32 = fromHashableInt(Hashable.FNV1_32)
  val FNV1A_32 = fromHashableInt(Hashable.FNV1A_32)
  val FNV1_64 = fromHashableLong(Hashable.FNV1_64)
  val FNV1A_64 = fromHashableLong(Hashable.FNV1A_64)
  /**
   * Ketama's default hash algorithm: the first 4 bytes of the MD5 as a little-endian int.
   * Wow, really? Who thought that was a good way to do it? :(
   */
  val KETAMA = fromHashableInt(Hashable.MD5_LEInt)
  val CRC32_ITU = fromHashableInt(Hashable.CRC32_ITU)
  val HSIEH = fromHashableInt(Hashable.HSIEH)
  val JENKINS = fromHashableLong(Hashable.JENKINS)

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
