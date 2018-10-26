package com.twitter.util

import com.twitter.util.Base64Long.{fromBase64, StandardBase64Alphabet, toBase64}
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.FunSuite
import org.scalatest.prop.GeneratorDrivenPropertyChecks

object Base64LongTest {
  private val BoundaryValues =
    Seq(0L, 1L, 63L, 64L, 4095L, 4096L, -1L, Long.MinValue, Long.MaxValue)

  private type Alphabet = PartialFunction[Int, Char]

  private def genAlphabet(g: Gen[Char]): Gen[Alphabet] = {
    def go(seen: List[Char], n: Int, iterations: Int): Gen[Alphabet] =
      if (n > 0 && iterations < 10000) {
        g.flatMap { c =>
          if (seen.contains(c)) go(seen, n, iterations + 1)
          else go(c :: seen, n - 1, iterations + 1)
        }
      } else {
        Gen.const(Base64Long.alphabet(seen))
      }

    go(Nil, 64, 0)
  }

  private implicit val arbAlphabet =
    Arbitrary[Alphabet](
      Gen.oneOf(
        Gen.const(StandardBase64Alphabet),
        // An alphabet that should trigger the general alphabet inversion
        // code
        genAlphabet(arbitrary[Char]),
        // An alphabet that should trigger the specialized alphabet
        // inversion code
        genAlphabet(Gen.oneOf(0.toChar.until(128.toChar).filterNot(Character.isISOControl)))
      )
    )

  private def expectedLength(n: Long): Int = {
    var k = n
    var i = 0
    while (k != 0) {
      i += 1
      assert(i < 12)
      k = k >>> 6
    }
    i.max(1) // The special case representation of zero has length 1
  }
}

class Base64LongTest extends FunSuite with GeneratorDrivenPropertyChecks {
  import Base64LongTest._

  private[this] def forAllLongs(f: (Alphabet, Long) => Unit): Unit = {
    BoundaryValues.foreach(f(StandardBase64Alphabet, _))
    forAll((a: Alphabet) => BoundaryValues.foreach(f(a, _)))
    forAll(f)
  }

  test("toBase64 properly converts zero") {
    assert(toBase64(0) == "A")

    val b = new StringBuilder
    forAll { (a: Alphabet) =>
      b.setLength(0)
      toBase64(b, 0, a)
      assert(b.result == a(0).toString)
    }
  }

  test("toBase64 properly converts a large number") {
    assert(toBase64(202128261025763330L) == "LOGpUdghAC")
  }

  test("toBase64 uses the expected number of digits") {
    BoundaryValues.foreach { (n: Long) =>
      assert(toBase64(n).length == expectedLength(n))
    }
    forAll((n: Long) => assert(toBase64(n).length == expectedLength(n)))

    val b = new StringBuilder
    forAllLongs { (a, n) =>
      b.setLength(0)
      toBase64(b, n, a)
      assert(b.length == expectedLength(n))
    }
  }

  test("fromBase64 is the inverse of toBase64") {
    BoundaryValues.foreach { l =>
      assert(l == fromBase64(toBase64(l)))
    }

    val b = new StringBuilder
    forAllLongs { (a, n) =>
      val inv = Base64Long.invertAlphabet(a)
      val start = b.length
      toBase64(b, n, a)
      assert(fromBase64(b, start, b.length, inv) == n)

      // Leading zeroes are dropped
      val start2 = b.length
      b.append(a(0))
      toBase64(b, n, a)
      assert(fromBase64(b, start2, b.length, inv) == n)
    }
  }

  test("fromBase64 throws an IllegalArgumentException exception for characters out of range") {
    forAll { (s: String, a: Alphabet) =>
      if (s.exists(!a.isDefinedAt(_))) {
        assertThrows[IllegalArgumentException](
          fromBase64(s, 0, s.length, Base64Long.invertAlphabet(a))
        )
      }
    }
  }

  test("fromBase64 throws an ArithmeticException when overflow is encountered") {
    // 2 ^ 64, or (1 << 64) which is an overflow
    // Q = 16 or 2^4 and each of the 10 'A's is 64 or 2^6
    val twoToThe64th = "QAAAAAAAAAA"
    assertThrows[ArithmeticException] {
      fromBase64(twoToThe64th)
    }
  }
}
