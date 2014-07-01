package com.twitter.util

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import com.twitter.conversions.storage._

@RunWith(classOf[JUnitRunner])
class StorageUnitTest extends FunSuite {
  test("StorageUnit: should convert whole numbers into storage units (back and forth)") {
    assert(1.byte.inBytes === 1)
    assert(1.kilobyte.inBytes === 1024)
    assert(1.megabyte.inMegabytes === 1.0)
    assert(1.gigabyte.inMegabytes === 1024.0)
    assert(1.gigabyte.inKilobytes === 1024.0 * 1024.0)
  }

  test("StorageUnit: should confer an essential humanity") {
    assert(900.bytes.toHuman === "900 B")
    assert(1.kilobyte.toHuman === "1024 B")
    assert(2.kilobytes.toHuman === "2.0 KiB")
    assert(Int.MaxValue.bytes.toHuman === "2.0 GiB")
    assert(Long.MaxValue.bytes.toHuman === "8.0 EiB")
  }

  test("StorageUnit: should accept humanity") {
    assert(StorageUnit.parse("142.bytes") === 142.bytes)
    assert(StorageUnit.parse("78.kilobytes") === 78.kilobytes)
    assert(StorageUnit.parse("1.megabyte") === 1.megabyte)
    assert(StorageUnit.parse("873.gigabytes") === 873.gigabytes)
    assert(StorageUnit.parse("3.terabytes") === 3.terabytes)
    assert(StorageUnit.parse("9.petabytes") === 9.petabytes)
    assert(StorageUnit.parse("-3.megabytes") === -3.megabytes)
  }

  test("StorageUnit: should reject soulless robots") {
    intercept[NumberFormatException] { StorageUnit.parse("100.bottles") }
    intercept[NumberFormatException] { StorageUnit.parse("100 bytes") }
  }

  test("StorageUnit: should deal with negative values") {
    assert(-123.bytes.inBytes === -123)
    assert(-2.kilobytes.toHuman === "-2.0 KiB")
  }

  test("StorageUnit: should min properly") {
    assert((1.bytes min 2.bytes) === 1.bytes)
    assert((2.bytes min 1.bytes) === 1.bytes)
    assert((2.bytes min 2.bytes) === 2.bytes)
  }

  test("StorageUnit: should adhere to company-issued serial number") {
    val i = 4.megabytes
    val j = 4.megabytes
    assert(i.hashCode === j.hashCode)
  }
}
