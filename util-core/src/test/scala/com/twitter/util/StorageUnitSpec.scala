package com.twitter.util

import org.specs.SpecificationWithJUnit
import com.twitter.conversions.storage._

class StorageUnitSpec extends SpecificationWithJUnit {
  "StorageUnit" should {
    "convert whole numbers into storage units (back and forth)" in {
      1.byte.inBytes mustEqual(1)
      1.kilobyte.inBytes mustEqual(1024)
      1.megabyte.inMegabytes mustEqual(1.0)
      1.gigabyte.inMegabytes mustEqual(1024.0)
      1.gigabyte.inKilobytes mustEqual(1024.0 * 1024.0)
    }

    "confer an essential humanity" in {
      900.bytes.toHuman mustEqual "900 B"
      1.kilobyte.toHuman mustEqual "1024 B"
      2.kilobytes.toHuman mustEqual "2.0 KiB"
      Int.MaxValue.bytes.toHuman mustEqual "2.0 GiB"
      Long.MaxValue.bytes.toHuman mustEqual "8.0 EiB"
    }

    "accept humanity" in {
      StorageUnit.parse("142.bytes") must be_==(142.bytes)
      StorageUnit.parse("78.kilobytes") must be_==(78.kilobytes)
      StorageUnit.parse("1.megabyte") must be_==(1.megabyte)
      StorageUnit.parse("873.gigabytes") must be_==(873.gigabytes)
      StorageUnit.parse("3.terabytes") must be_==(3.terabytes)
      StorageUnit.parse("9.petabytes") must be_==(9.petabytes)
    }

    "reject soulless robots" in {
      StorageUnit.parse("100.bottles") must throwA[NumberFormatException]
      StorageUnit.parse("100 bytes") must throwA[NumberFormatException]
    }
  }
}
