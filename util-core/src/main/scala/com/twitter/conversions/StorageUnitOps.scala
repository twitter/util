package com.twitter.conversions

import com.twitter.util.StorageUnit

/**
 * Implicits for writing readable [[StorageUnit]]s.
 *
 * @example
 * {{{
 * import com.twitter.conversions.StorageUnitOps._
 *
 * 5.bytes
 * 1.kilobyte
 * 256.gigabytes
 * }}}
 */
object StorageUnitOps {

  implicit class RichLong(val numBytes: Long) extends AnyVal {
    def byte: StorageUnit = bytes
    def bytes: StorageUnit = StorageUnit.fromBytes(numBytes)
    def kilobyte: StorageUnit = kilobytes
    def kilobytes: StorageUnit = StorageUnit.fromKilobytes(numBytes)
    def megabyte: StorageUnit = megabytes
    def megabytes: StorageUnit = StorageUnit.fromMegabytes(numBytes)
    def gigabyte: StorageUnit = gigabytes
    def gigabytes: StorageUnit = StorageUnit.fromGigabytes(numBytes)
    def terabyte: StorageUnit = terabytes
    def terabytes: StorageUnit = StorageUnit.fromTerabytes(numBytes)
    def petabyte: StorageUnit = petabytes
    def petabytes: StorageUnit = StorageUnit.fromPetabytes(numBytes)

    def thousand: Long = numBytes * 1000
    def million: Long = numBytes * 1000 * 1000
    def billion: Long = numBytes * 1000 * 1000 * 1000
  }
}
