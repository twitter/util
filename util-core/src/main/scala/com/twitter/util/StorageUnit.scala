/*
 * Copyright 2010 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.util

object StorageUnit {
  val infinite = new StorageUnit(Long.MaxValue)
  val zero = new StorageUnit(0)

  private def factor(s: String) = {
    var lower = s.toLowerCase
    if (lower endsWith "s")
      lower = lower dropRight 1

    lower match {
      case "byte" => 1L
      case "kilobyte" => 1L<<10
      case "megabyte" => 1L<<20
      case "gigabyte" => 1L<<30
      case "terabyte" => 1L<<40
      case "petabyte" => 1L<<50
      case "exabyte" => 1L<<60
      case badUnit => throw new NumberFormatException(
        "Unrecognized unit %s".format(badUnit))
    }
  }

  def parse(s: String): StorageUnit = s.split("\\.") match {
    case Array(v, u) =>
      val vv = v.toLong
      val uu = factor(u)
      new StorageUnit(vv*uu)

    case _ =>
      throw new NumberFormatException("invalid storage unit string")
  }
}

/**
 * Representation of storage units.
 *
 * If you import the [[com.twitter.conversions.storage]] implicits you can
 * write human-readable values such as `1.gigabyte` or `50.megabytes`.
 */
class StorageUnit(val bytes: Long) extends Ordered[StorageUnit] {
  def inBytes     = bytes
  def inKilobytes = bytes / (1024L)
  def inMegabytes = bytes / (1024L * 1024)
  def inGigabytes = bytes / (1024L * 1024 * 1024)
  def inTerabytes = bytes / (1024L * 1024 * 1024 * 1024)
  def inPetabytes = bytes / (1024L * 1024 * 1024 * 1024 * 1024)
  def inExabytes  = bytes / (1024L * 1024 * 1024 * 1024 * 1024 * 1024)

  def +(that: StorageUnit): StorageUnit = new StorageUnit(this.bytes + that.bytes)
  def -(that: StorageUnit): StorageUnit = new StorageUnit(this.bytes - that.bytes)
  def *(scalar: Double): StorageUnit = new StorageUnit((this.bytes.toDouble*scalar).toLong)
  def *(scalar: Long): StorageUnit = new StorageUnit(this.bytes * scalar)
  def /(scalar: Long): StorageUnit = new StorageUnit(this.bytes / scalar)

  override def equals(other: Any) = {
    other match {
      case other: StorageUnit =>
        inBytes == other.inBytes
      case _ =>
        false
    }
  }

  override def hashCode: Int = bytes.hashCode

  override def compare(other: StorageUnit) =
    if (bytes < other.bytes) -1 else if (bytes > other.bytes) 1 else 0

  def min(other: StorageUnit): StorageUnit =
    if (this < other) this else other

  def max(other: StorageUnit): StorageUnit =
    if (this > other) this else other

  override def toString() = inBytes + ".bytes"

  def toHuman(): String = {
    val prefix = "KMGTPE"
    var prefixIndex = -1
    var display = bytes.toDouble.abs
    while (display > 1126.0) {
      prefixIndex += 1
      display /= 1024.0
    }
    if (prefixIndex < 0) {
      "%d B".format(bytes)
    } else {
      "%.1f %ciB".format(display*bytes.signum, prefix.charAt(prefixIndex))
    }
  }
}
