package com.twitter.util

import org.specs.SpecificationWithJUnit
import scala.util.Random

class LongOverflowArithSpec extends SpecificationWithJUnit {
  "LongOverflowArith" should {
    val random = new Random
    val maxSqrt = 3037000499L

    def randLong() = {
      if (random.nextInt > 0)
        random.nextLong() % maxSqrt
      else
        random.nextLong()
    }

    "add" in {
      def test(a: Long, b: Long) {
        val bigC = BigInt(a) + BigInt(b)
        if (bigC.abs > Long.MaxValue)
          LongOverflowArith.add(a, b) must throwA[LongOverflowException]
        else
          LongOverflowArith.add(a, b) mustEqual bigC.toLong
      }

      for (i <- 0 until 1000) {
        test(randLong(), randLong())
      }
    }

    "sub" in {
      def test(a: Long, b: Long) {
        val bigC = BigInt(a) - BigInt(b)
        if (bigC.abs > Long.MaxValue)
          LongOverflowArith.sub(a, b) must throwA[LongOverflowException]
        else
          LongOverflowArith.sub(a, b) mustEqual bigC.toLong
      }

      for (i <- 0 until 1000) {
        test(randLong(), randLong())
      }
    }

    "mul" in {
      LongOverflowArith.mul(0L, 10L) mustEqual 0L
      LongOverflowArith.mul(1L, 11L) mustEqual 11L
      LongOverflowArith.mul(-1L, -11L) mustEqual 11L
      LongOverflowArith.mul(-1L, 22L) mustEqual -22L
      LongOverflowArith.mul(22L, -1L) mustEqual -22L

      LongOverflowArith.mul(3456116450671355229L, -986247066L) must throwA[LongOverflowException]

      LongOverflowArith.mul(Long.MaxValue, 1L) mustEqual Long.MaxValue
      LongOverflowArith.mul(Long.MaxValue - 1L, 9L) must throwA[LongOverflowException]

      LongOverflowArith.mul(Long.MinValue, 2L) must throwA[LongOverflowException]
      LongOverflowArith.mul(Long.MinValue, -2L) must throwA[LongOverflowException]
      LongOverflowArith.mul(Long.MinValue, 3L) must throwA[LongOverflowException]
      LongOverflowArith.mul(Long.MinValue, -3L) must throwA[LongOverflowException]
      LongOverflowArith.mul(Long.MinValue, 1L) mustEqual Long.MinValue
      LongOverflowArith.mul(Long.MinValue, -1L) must throwA[LongOverflowException]
      LongOverflowArith.mul(1L, Long.MinValue) mustEqual Long.MinValue
      LongOverflowArith.mul(-1L, Long.MinValue) must throwA[LongOverflowException]
      LongOverflowArith.mul(Long.MinValue, 0L) mustEqual 0L
      LongOverflowArith.mul(Long.MinValue + 1L, 2L) must throwA[LongOverflowException]

      def test(a: Long, b: Long) {
        val bigC = BigInt(a) * BigInt(b)
        if (bigC.abs > Long.MaxValue)
          LongOverflowArith.mul(a, b) must throwA[LongOverflowException]
        else
          LongOverflowArith.mul(a, b) mustEqual bigC.toLong
      }

      for (i <- 0 until 1000) {
        val a = randLong()
        val b = randLong()
        try {
          test(a, b)
        } catch {
          case x => {
            println(a + " * " + b + " failed")
            throw x
          }
        }
      }
    }
  }
}
