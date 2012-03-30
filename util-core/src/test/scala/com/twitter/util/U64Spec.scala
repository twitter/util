package com.twitter.util

import util.Random

import org.specs.SpecificationWithJUnit

class U64Spec extends SpecificationWithJUnit {
  import U64._

  "comparable" in {
    {
      val a = 0x0000000000000001L
      a must be_==(1)
      val b = 0x0000000000000002L
      b must be_==(2)

      a.u64_<(b) must beTrue
      b.u64_<(a) must beFalse
    }

    {
      val a = 0xFFFFFFFFFFFFFFFFL
      a must be_==(-1)
      val b = 0xFFFFFFFFFFFFFFFEL
      b must be_==(-2)

      a.u64_<(b) must beFalse
      b.u64_<(a) must beTrue
    }

    {
      val a = 0xFFFFFFFFFFFFFFFFL
      a must be_==(-1)
      val b = 0x0000000000000001L
      b must be_==(1)

      a.u64_<(b) must beFalse
      b.u64_<(a) must beTrue
    }
  }

  "comparable in range" in {
    0L.u64_within(0, 1)    must beFalse
    0L.u64_contained(0, 1) must beTrue

    // (inverted range)
    0L.u64_within(-1, 1) must beFalse
    1L.u64_within(-1, 1) must beFalse
    2L.u64_within(-1, 1) must beFalse

    0xFFFFFFFFFFFFFFFEL.u64_within(0xFFFFFFFFFFFFFFFDL, 0xFFFFFFFFFFFFFFFFL) must beTrue
    0xFFFFFFFFFFFFFFFDL.u64_within(0xFFFFFFFFFFFFFFFDL, 0xFFFFFFFFFFFFFFFFL) must beFalse
    0xFFFFFFFFFFFFFFFFL.u64_within(0xFFFFFFFFFFFFFFFDL, 0xFFFFFFFFFFFFFFFFL) must beFalse

    0xFFFFFFFFFFFFFFFEL.u64_contained(0xFFFFFFFFFFFFFFFDL, 0xFFFFFFFFFFFFFFFFL) must beTrue
    0xFFFFFFFFFFFFFFFDL.u64_contained(0xFFFFFFFFFFFFFFFDL, 0xFFFFFFFFFFFFFFFFL) must beTrue
    0xFFFFFFFFFFFFFFFFL.u64_contained(0xFFFFFFFFFFFFFFFDL, 0xFFFFFFFFFFFFFFFFL) must beTrue

    // Bit flip area!
    0x7FFFFFFFFFFFFFFFL.u64_within(0x7FFFFFFFFFFFFFFFL, 0x8000000000000000L) must beFalse
    0x8000000000000000L.u64_within(0x7FFFFFFFFFFFFFFFL, 0x8000000000000000L) must beFalse

    0x7FFFFFFFFFFFFFFFL.u64_contained(0x7FFFFFFFFFFFFFFFL, 0x8000000000000000L) must beTrue
    0x8000000000000000L.u64_contained(0x7FFFFFFFFFFFFFFFL, 0x8000000000000000L) must beTrue

    0x7FFFFFFFFFFFFFFAL.u64_within(0x7FFFFFFFFFFFFFFAL, 0x800000000000000AL) must beFalse
    0x7FFFFFFFFFFFFFFBL.u64_within(0x7FFFFFFFFFFFFFFAL, 0x800000000000000AL) must beTrue
    0x7FFFFFFFFFFFFFFFL.u64_within(0x7FFFFFFFFFFFFFFAL, 0x800000000000000AL) must beTrue
    0x8000000000000000L.u64_within(0x7FFFFFFFFFFFFFFAL, 0x800000000000000AL) must beTrue
    0x8000000000000001L.u64_within(0x7FFFFFFFFFFFFFFAL, 0x800000000000000AL) must beTrue
    0x8000000000000009L.u64_within(0x7FFFFFFFFFFFFFFAL, 0x800000000000000AL) must beTrue
    0x800000000000000AL.u64_within(0x7FFFFFFFFFFFFFFAL, 0x800000000000000AL) must beFalse
  }

  "divisible" in {
    10L.u64_/(5L) must be_==(2L)
    0xFFFFFFFFFFFFFFFFL / 0x0FFFFFFFFFFFFFFFL must be_==(0L)
    0xFFFFFFFFFFFFFFFFL.u64_/(0x0FFFFFFFFFFFFFFFL) must be_==(16L)

    0x7FFFFFFFFFFFFFFFL.u64_/(2) must be_==(0x3FFFFFFFFFFFFFFFL)

    0x8000000000000000L / 2 must be_==(0xc000000000000000L)
    0x8000000000000000L.u64_/(2) must be_==(0x4000000000000000L)

    0x8000000000000000L.u64_/(0x8000000000000000L) must be_==(1)
    0x8000000000000000L.u64_/(0x8000000000000001L) must be_==(0)

    0xFF00000000000000L.u64_/(0x0F00000000000000L) must be_==(0x11L)
    0x8F00000000000000L.u64_/(0x100) must be_==(0x008F000000000000L)
    0x8000000000000000L.u64_/(3) must be_==(0x2AAAAAAAAAAAAAAAL)
  }

  "ids" in {
    "survive conversions" in {
      val rng = new Random

      (0 until 10000).foreach { _ =>
        val id = rng.nextLong
        id must be equalTo(id.toU64ByteArray.toU64Long)
        id must be equalTo(id.toU64ByteArray.toU64HexString.toU64ByteArray.toU64Long)
      }
    }

    "be serializable" in {
      0L.toU64HexString must be_==("0000000000000000")
      0x0102030405060700L.toU64HexString must be_==("0102030405060700")
      0xFFF1F2F3F4F5F6F7L.toU64HexString must be_==("fff1f2f3f4f5f6f7")
    }

    "convert from short hex string" in {
      new RichU64String("7b").toU64Long mustEqual 123L
    }
  }
}
