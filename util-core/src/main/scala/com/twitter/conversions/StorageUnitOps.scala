package com.twitter.conversions

import com.twitter.util.StorageUnit

import scala.language.implicitConversions

/**
 * Implicits for writing readable [[com.twitter.util.StorageUnit]]s.
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

  implicit class RichStorageUnit(val numBytes: Long) extends AnyVal {
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

  /**
   * Forwarder for Int, as Scala 3.0 doesn't seem to do the implicit conversion to Long anymore.
   * This is not a bug, as Scala 2.13 already had a flag ("-Ywarn-implicit-widening") to turn on warnings/errors
   * for that.
   *
   * The implicit conversion from Int to Long here doesn't lose precision and keeps backwards source compatibliity
   * with previous releases.
   */
  implicit def richStorageUnitFromInt(numBytes: Int): RichStorageUnit =
    new RichStorageUnit(numBytes.toLong)
}
