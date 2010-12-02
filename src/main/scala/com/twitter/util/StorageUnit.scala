package com.twitter.util

object StorageUnitConversions {
  class RichWholeNumber(wrapped: Long) {
    require(wrapped > 0, "Negative storage units are useful but unsupported")

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
  }

  implicit def intToStorageUnitableWholeNumber(i: Int) = new RichWholeNumber(i)
  implicit def longToStorageUnitableWholeNumber(l: Long) = new RichWholeNumber(l)
}

class StorageUnit(bytes: Long) {
  require(bytes > 0, "Negative storage units are useful but unsupported")

  def inBytes = bytes
  def inKilobytes = bytes / (1024L)
  def inMegabytes = bytes / (1024L * 1024)
  def inGigabytes = bytes / (1024L * 1024 * 1024)
  def inTerabytes = bytes / (1024L * 1024 * 1024 * 1024)
  def inPetabytes = bytes / (1024L * 1024 * 1024 * 1024 * 1024)
}