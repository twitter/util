package com.twitter.io

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.charset.StandardCharsets
import java.util.Arrays
import org.scalatest.funsuite.AnyFunSuite

class TempFileTest extends AnyFunSuite {

  test("TempFile should load resources") {
    val f1 = TempFile.fromResourcePath("/java/lang/String.class")
    val f2 = TempFile.fromResourcePath(getClass, "/java/lang/String.class")
    val f3 = TempFile.fromSystemResourcePath("java/lang/String.class")
    // Tests the basename allows basename to be less than 3 characters.
    val f4 = TempFile.fromResourcePath("/1.txt")

    val c1 = Files.readBytes(f1)
    val c2 = Files.readBytes(f2)
    val c3 = Files.readBytes(f3)
    val c4 = Files.readBytes(f4)

    assert(Arrays.equals(c1, c2))
    assert(Arrays.equals(c2, c3))
    assert(new DataInputStream(new ByteArrayInputStream(c1)).readInt == 0xcafebabe)
    assert(new String(c4, StandardCharsets.UTF_8) == "Lorem ipsum dolor sit amet\n")
  }
}
