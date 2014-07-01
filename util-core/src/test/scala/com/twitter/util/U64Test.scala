package com.twitter.util

import scala.util.Random

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner


@RunWith(classOf[JUnitRunner])
class U64Test extends WordSpec {
  import U64._

  "comparable" in {
    {
      val a = 0x0000000000000001L
      assert(a === 1)
      val b = 0x0000000000000002L
      assert(b === 2)

      assert(a.u64_<(b) === true)
      assert(b.u64_<(a) === false)
    }

    {
      val a = 0xFFFFFFFFFFFFFFFFL
      assert(a === -1)
      val b = 0xFFFFFFFFFFFFFFFEL
      assert(b === -2)

      assert(a.u64_<(b) === false)
      assert(b.u64_<(a) === true)
    }

    {
      val a = 0xFFFFFFFFFFFFFFFFL
      assert(a === -1)
      val b = 0x0000000000000001L
      assert(b === 1)

      assert(a.u64_<(b) === false)
      assert(b.u64_<(a) === true)
    }
  }

  "comparable in range" in {
    assert(0L.u64_within(0, 1)    === false)
    assert(0L.u64_contained(0, 1) === true)

    // (inverted range)
    assert(0L.u64_within(-1, 1) === false)
    assert(1L.u64_within(-1, 1) === false)
    assert(2L.u64_within(-1, 1) === false)

    assert(0xFFFFFFFFFFFFFFFEL.u64_within(0xFFFFFFFFFFFFFFFDL, 0xFFFFFFFFFFFFFFFFL) === true)
    assert(0xFFFFFFFFFFFFFFFDL.u64_within(0xFFFFFFFFFFFFFFFDL, 0xFFFFFFFFFFFFFFFFL) === false)
    assert(0xFFFFFFFFFFFFFFFFL.u64_within(0xFFFFFFFFFFFFFFFDL, 0xFFFFFFFFFFFFFFFFL) === false)

    assert(0xFFFFFFFFFFFFFFFEL.u64_contained(0xFFFFFFFFFFFFFFFDL, 0xFFFFFFFFFFFFFFFFL) === true)
    assert(0xFFFFFFFFFFFFFFFDL.u64_contained(0xFFFFFFFFFFFFFFFDL, 0xFFFFFFFFFFFFFFFFL) === true)
    assert(0xFFFFFFFFFFFFFFFFL.u64_contained(0xFFFFFFFFFFFFFFFDL, 0xFFFFFFFFFFFFFFFFL) === true)

    // Bit flip area!
    assert(0x7FFFFFFFFFFFFFFFL.u64_within(0x7FFFFFFFFFFFFFFFL, 0x8000000000000000L) === false)
    assert(0x8000000000000000L.u64_within(0x7FFFFFFFFFFFFFFFL, 0x8000000000000000L) === false)

    assert(0x7FFFFFFFFFFFFFFFL.u64_contained(0x7FFFFFFFFFFFFFFFL, 0x8000000000000000L) === true)
    assert(0x8000000000000000L.u64_contained(0x7FFFFFFFFFFFFFFFL, 0x8000000000000000L) === true)

    assert(0x7FFFFFFFFFFFFFFAL.u64_within(0x7FFFFFFFFFFFFFFAL, 0x800000000000000AL) === false)
    assert(0x7FFFFFFFFFFFFFFBL.u64_within(0x7FFFFFFFFFFFFFFAL, 0x800000000000000AL) === true)
    assert(0x7FFFFFFFFFFFFFFFL.u64_within(0x7FFFFFFFFFFFFFFAL, 0x800000000000000AL) === true)
    assert(0x8000000000000000L.u64_within(0x7FFFFFFFFFFFFFFAL, 0x800000000000000AL) === true)
    assert(0x8000000000000001L.u64_within(0x7FFFFFFFFFFFFFFAL, 0x800000000000000AL) === true)
    assert(0x8000000000000009L.u64_within(0x7FFFFFFFFFFFFFFAL, 0x800000000000000AL) === true)
    assert(0x800000000000000AL.u64_within(0x7FFFFFFFFFFFFFFAL, 0x800000000000000AL) === false)
  }

  "divisible" in {
    assert(10L.u64_/(5L) === 2L)
    assert(0xFFFFFFFFFFFFFFFFL / 0x0FFFFFFFFFFFFFFFL === 0L)
    assert(0xFFFFFFFFFFFFFFFFL.u64_/(0x0FFFFFFFFFFFFFFFL) === 16L)

    assert(0x7FFFFFFFFFFFFFFFL.u64_/(2) === 0x3FFFFFFFFFFFFFFFL)

    assert(0x8000000000000000L / 2 === 0xc000000000000000L)
    assert(0x8000000000000000L.u64_/(2) === 0x4000000000000000L)

    assert(0x8000000000000000L.u64_/(0x8000000000000000L) === 1)
    assert(0x8000000000000000L.u64_/(0x8000000000000001L) === 0)

    assert(0xFF00000000000000L.u64_/(0x0F00000000000000L) === 0x11L)
    assert(0x8F00000000000000L.u64_/(0x100) === 0x008F000000000000L)
    assert(0x8000000000000000L.u64_/(3) === 0x2AAAAAAAAAAAAAAAL)
  }

  "ids" should {
    "survive conversions" in {
      val rng = new Random

      (0 until 10000).foreach { _ =>
        val id = rng.nextLong
        assert(id === (id.toU64ByteArray.toU64Long))
        assert(id === (id.toU64ByteArray.toU64HexString.toU64ByteArray.toU64Long))
      }
    }

    "be serializable" in {
      assert(0L.toU64HexString === "0000000000000000")
      assert(0x0102030405060700L.toU64HexString === "0102030405060700")
      assert(0xFFF1F2F3F4F5F6F7L.toU64HexString === "fff1f2f3f4f5f6f7")
    }

    "convert from short hex string" in {
      assert(new RichU64String("7b").toU64Long === 123L)
    }

    "don't silently truncate" in {
      intercept[NumberFormatException] {
        new RichU64String("318528893302738945")
      }
    }
  }
}
