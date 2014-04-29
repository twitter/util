package com.twitter.io

import java.io.IOException
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

@RunWith(classOf[JUnitRunner])
class BufInputStreamTest extends FunSuite {
  private[this] val fileString = "Test_All_Tests\nTest_java_io_BufferedInputStream\nTest_java_io_BufferedOutputStream\nTest_ByteArrayInputStream\nTest_java_io_ByteArrayOutputStream\nTest_java_io_DataInputStream\n"
  private[this] val fileBuf = Buf.ByteArray(fileString.getBytes)

  test("Constructor") {
    val is = new BufInputStream(fileBuf)
    assert(is.available() == fileString.length())
  }

  test("available") {
    val is = new BufInputStream(fileBuf)
    assert(is.available() == fileString.length(), "Returned incorrect number of available bytes")
  }

  test("close") {
    val is = new BufInputStream(fileBuf)

    val i = is.read()
    assert(i != -1)
    try {
      is.close()
    } catch { case e: IOException =>
        fail("Test 1: Failed to close the input stream.")
    }
    try {
      val j = is.read()
      assert(j != -1)
    } catch { case e: Exception =>
        fail("Test 2: Should be able to read from closed stream.")
    }
  }

  test("markI") {
    val is = new BufInputStream(fileBuf)

    // Test for method void java.io.ByteArrayInputStream.mark(int)
    val array1 = new Array[Byte](100)
    val array2 = new Array[Byte](100)
    try {
      is.skip(3000)
      is.mark(1000)
      is.read(array1, 0, array1.length)
      is.reset()
      is.read(array2, 0, array2.length)
      is.reset()
      val s1 = new String(array1, 0, array1.length)
      val s2 = new String(array2, 0, array2.length)
      assert(s1.equals(s2), "Failed to mark correct position")
    } catch { case e: Exception =>
        fail("Exception during mark test")
    }
  }

  test("markSupported") {
    val is = new BufInputStream(fileBuf)
    assert(is.markSupported(), "markSupported returned incorrect value")
  }

  test("read one") {
    val is = new BufInputStream(fileBuf)
    val c = is.read()
    is.reset()
    assert(c == fileString.charAt(0), "read returned incorrect char %s %s".format(c, fileString.charAt(0)))
  }

  test("read") {
    val is = new BufInputStream(fileBuf)
    val array = new Array[Byte](20)
    is.skip(50)
    is.mark(100)
    is.read(array, 0, array.length)
    val s1 = new String(array, 0, array.length)
    val s2 = fileString.substring(50, 70)
    assert(s1.equals(s2), "Failed to read correct data.")
  }

  test("read into null array") {
    val is = new BufInputStream(fileBuf)

    intercept[NullPointerException] {
      is.read(null, 0, 1)
      fail("NullPointerException expected.")
    }
  }

  test("read into offset < 0") {
    val is = new BufInputStream(fileBuf)
    val array = new Array[Byte](20)

    intercept[IndexOutOfBoundsException] {
      is.read(array , -1, 1)
      fail("IndexOutOfBoundsException expected.")
    }
  }

  test("read negative len bytes") {
    val is = new BufInputStream(fileBuf)
    val array = new Array[Byte](20)

    intercept[IllegalArgumentException] {
      is.read(array , 1, -1)
      fail("IllegalArgumentException expected.")
    }
  }

  test("read beyond end of array") {
    val is = new BufInputStream(fileBuf)
    val array = new Array[Byte](20)

    intercept[IndexOutOfBoundsException] {
      is.read(array, 1, array.length)
      fail("IndexOutOfBoundsException expected.")
    }

    intercept[IndexOutOfBoundsException] {
      is.read(array, array.length, array.length)
      fail("IndexOutOfBoundsException expected.")
    }
  }

  test("reset") {
    val is = new BufInputStream(fileBuf)
    // Test for method void java.io.ByteArrayInputStream.reset()
    val array1 = new Array[Byte](10)
    val array2 = new Array[Byte](10)
    is.mark(200)
    is.read(array1, 0, 10)
    is.reset()
    is.read(array2, 0, 10)
    is.reset()

    val s1 = new String(array1, 0, array1.length)
    val s2 = new String(array2, 0, array2.length)
    assert(s1.equals(s2), "Reset failed")
  }

  test("skip") {
    val is = new BufInputStream(fileBuf)
    val array1 = new Array[Byte](10)
    is.skip(100)
    is.read(array1, 0, array1.length)
    val s1 = new String(array1, 0, array1.length)
    val s2 = fileString.substring(100, 110)
    assert(s1.equals(s2), "Failed to skip to correct position")
  }

  test("read len=0 from non-empty stream should return 0") {
    val is = new BufInputStream(fileBuf)
    val array = new Array[Byte](1)
    assert(is.read(array, 0, 0) == 0)
  }

  test("read len >= 0 from exhausted stream should return -1") {
    val is = new BufInputStream(fileBuf)
    val array = new Array[Byte](10000)
    val c = is.read(array, 0, array.length)
    assert(c == fileBuf.length, "Stream should have been exhausted")
    assert(is.read(array, c, 0) == -1, "Stream should have repored exhaustion")
    assert(is.read(array, c, array.length - c) == -1, "Stream should have repored exhaustion")
  }
}
