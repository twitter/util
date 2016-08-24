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

package com.twitter.conversions

import com.twitter.util.StorageUnit
import scala.language.implicitConversions

object storage {
  class RichWholeNumber(wrapped: Long) {
    def byte: StorageUnit      = bytes
    def bytes: StorageUnit     = StorageUnit.fromBytes(wrapped)
    def kilobyte: StorageUnit  = kilobytes
    def kilobytes: StorageUnit = StorageUnit.fromKilobytes(wrapped)
    def megabyte: StorageUnit  = megabytes
    def megabytes: StorageUnit = StorageUnit.fromMegabytes(wrapped)
    def gigabyte: StorageUnit  = gigabytes
    def gigabytes: StorageUnit = StorageUnit.fromGigabytes(wrapped)
    def terabyte: StorageUnit  = terabytes
    def terabytes: StorageUnit = StorageUnit.fromTerabytes(wrapped)
    def petabyte: StorageUnit  = petabytes
    def petabytes: StorageUnit = StorageUnit.fromPetabytes(wrapped)

    def thousand: Long  = wrapped * 1000
    def million: Long   = wrapped * 1000 * 1000
    def billion: Long   = wrapped * 1000 * 1000 * 1000
  }

  implicit def intToStorageUnitableWholeNumber(i: Int): RichWholeNumber = new RichWholeNumber(i)
  implicit def longToStorageUnitableWholeNumber(l: Long): RichWholeNumber = new RichWholeNumber(l)
}
