package com.twitter.util

import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import scala.util.Random
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LongOverflowArithTest extends WordSpec with ShouldMatchers {
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
          intercept[LongOverflowException] {
          LongOverflowArith.add(a, b)
          }
        else
          LongOverflowArith.add(a, b) shouldEqual bigC.toLong
      }

      for (i <- 0 until 1000) {
        test(randLong(), randLong())
      }
    }

    "sub" in {
      def test(a: Long, b: Long) {
        val bigC = BigInt(a) - BigInt(b)
        if (bigC.abs > Long.MaxValue)
          intercept[LongOverflowException] {
            LongOverflowArith.sub(a, b)
          }
        else
          LongOverflowArith.sub(a, b) shouldEqual bigC.toLong
      }

      for (i <- 0 until 1000) {
        test(randLong(), randLong())
      }
    }

    "mul" in {
      LongOverflowArith.mul(0L, 10L) shouldEqual 0L
      LongOverflowArith.mul(1L, 11L) shouldEqual 11L
      LongOverflowArith.mul(-1L, -11L) shouldEqual 11L
      LongOverflowArith.mul(-1L, 22L) shouldEqual -22L
      LongOverflowArith.mul(22L, -1L) shouldEqual -22L

      intercept[LongOverflowException] {
        LongOverflowArith.mul(3456116450671355229L, -986247066L)
      }

      LongOverflowArith.mul(Long.MaxValue, 1L) shouldEqual Long.MaxValue

      intercept[LongOverflowException] {
        LongOverflowArith.mul(Long.MaxValue - 1L, 9L)
      }

      intercept[LongOverflowException] {
        LongOverflowArith.mul(Long.MinValue, 2L)
      }
      intercept[LongOverflowException] {
        LongOverflowArith.mul(Long.MinValue, -2L)
      }
      intercept[LongOverflowException] {
        LongOverflowArith.mul(Long.MinValue, 3L)
      }
      intercept[LongOverflowException] {
        LongOverflowArith.mul(Long.MinValue, -3L)
      }
      LongOverflowArith.mul(Long.MinValue, 1L) shouldEqual Long.MinValue
      intercept[LongOverflowException] {
        LongOverflowArith.mul(Long.MinValue, -1L)
      }
      LongOverflowArith.mul(1L, Long.MinValue) shouldEqual Long.MinValue
      intercept[LongOverflowException] {
        LongOverflowArith.mul(-1L, Long.MinValue)
      }
      LongOverflowArith.mul(Long.MinValue, 0L) shouldEqual 0L
      intercept[LongOverflowException] {
        LongOverflowArith.mul(Long.MinValue + 1L, 2L)
      }

      def test(a: Long, b: Long) {
        val bigC = BigInt(a) * BigInt(b)
        if (bigC.abs > Long.MaxValue)
          intercept[LongOverflowException] {
            LongOverflowArith.mul(a, b)
          }
        else
          LongOverflowArith.mul(a, b) shouldEqual bigC.toLong
      }

      for (i <- 0 until 1000) {
        val a = randLong()
        val b = randLong()
        try {
          test(a, b)
        } catch {
          case x: Throwable => {
            println(a + " * " + b + " failed")
            throw x
          }
        }
      }
    }
  }
}
