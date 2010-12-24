package com.twitter.util

import org.specs.Specification
import com.twitter.conversions.storage._

object StorageUnitSpec extends Specification {
  "StorageUnit" should {
    "convert whole numbers into storage units (back and forth)" in {
      1.byte.inBytes mustEqual(1)
      1.kilobyte.inBytes mustEqual(1024)
      1.megabyte.inMegabytes mustEqual(1.0)
      1.gigabyte.inMegabytes mustEqual(1024.0)
      1.gigabyte.inKilobytes mustEqual(1024.0 * 1024.0)
    }
  }
}
