package com.twitter.util

object StorageUnitConversions {
  class RichWholeNumber(wrapped: Long) {
    def byte = bytes
    def bytes = new StorageUnit(wrapped)
    def kilobyte = kilobytes
    def kilobytes = new StorageUnit(wrapped * 1024)
    def megabyte = megabytes
    def megabytes = new StorageUnit(wrapped * 1024 * 1024)
    def gigabyte = gigabytes
    def gigabytes = new StorageUnit(wrapped * 1024 * 1024 * 1024)
    def terabyte = terabytes
    def terabytes = new StorageUnit(wrapped * 1024 * 1024 * 1024 * 1024)
    def petabyte = petabytes
    def petabytes = new StorageUnit(wrapped * 1024 * 1024 * 1024 * 1024 * 1024)
  }

  implicit def intToStorageUnitableWholeNumber(i: Int) = new RichWholeNumber(i)
  implicit def longToStorageUnitableWholeNumber(l: Long) = new RichWholeNumber(l)
}

class StorageUnit(bytes: Long) {
  def inBytes = bytes
  def inKilobytes = bytes / Math.pow(1024, 1)
  def inMegabytes = bytes / Math.pow(1024, 2)
  def inGigabytes = bytes / Math.pow(1024, 3)
  def inTerabytes = bytes / Math.pow(1024, 4)
  def inPetabytes = bytes / Math.pow(1024, 5)
}