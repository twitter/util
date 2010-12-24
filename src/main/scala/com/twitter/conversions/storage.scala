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

package com.twitter
package conversions

import com.twitter.util.StorageUnit

object storage {
  class RichWholeNumber(wrapped: Long) {
    require(wrapped > 0, "Negative storage units are unsupported")

    def byte      = bytes
    def bytes     = new StorageUnit(wrapped)
    def kilobyte  = kilobytes
    def kilobytes = new StorageUnit(wrapped * 1024)
    def megabyte  = megabytes
    def megabytes = new StorageUnit(wrapped * 1024 * 1024)
    def gigabyte  = gigabytes
    def gigabytes = new StorageUnit(wrapped * 1024 * 1024 * 1024)
    def terabyte  = terabytes
    def terabytes = new StorageUnit(wrapped * 1024 * 1024 * 1024 * 1024)
    def petabyte  = petabytes
    def petabytes = new StorageUnit(wrapped * 1024 * 1024 * 1024 * 1024 * 1024)

    def thousand  = wrapped * 1000
    def million   = wrapped * 1000 * 1000
    def billion   = wrapped * 1000 * 1000 * 1000
  }

  implicit def intToStorageUnitableWholeNumber(i: Int) = new RichWholeNumber(i)
  implicit def longToStorageUnitableWholeNumber(l: Long) = new RichWholeNumber(l)
}
