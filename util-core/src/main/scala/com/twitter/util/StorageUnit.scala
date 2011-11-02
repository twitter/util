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
}

/**
 * Representation of storage units.
 *
 * If you import the [[com.twitter.conversions.storage]] implicits you can
 * write human-readable values such as `1.gigabyte` or `50.megabytes`.
 */
class StorageUnit(val bytes: Long) extends Ordered[StorageUnit] {
  require(bytes >= 0, "Negative storage units are useful[Citation Needed] but unsupported")

  def inBytes     = bytes
  def inKilobytes = bytes / (1024L)
  def inMegabytes = bytes / (1024L * 1024)
  def inGigabytes = bytes / (1024L * 1024 * 1024)
  def inTerabytes = bytes / (1024L * 1024 * 1024 * 1024)
  def inPetabytes = bytes / (1024L * 1024 * 1024 * 1024 * 1024)
  def inExabytes  = bytes / (1024L * 1024 * 1024 * 1024 * 1024 * 1024)

  override def equals(other: Any) = {
    other match {
      case other: StorageUnit =>
        inBytes == other.inBytes
      case _ =>
        false
    }
  }

  override def compare(other: StorageUnit) = if (bytes < other.bytes) -1 else if (bytes > other.bytes) 1 else 0

  override def toString() = inBytes + ".bytes"

  def toHuman(): String = {
    val prefix = "KMGTPE"
    var prefixIndex = -1
    var display = bytes.toDouble
    while (display > 1126.0) {
      prefixIndex += 1
      display /= 1024.0
    }
    if (prefixIndex < 0) {
      "%d B".format(bytes)
    } else {
      "%.1f %ciB".format(display, prefix.charAt(prefixIndex))
    }
  }
}
