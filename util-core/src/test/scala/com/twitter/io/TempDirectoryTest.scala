package com.twitter.io

import org.scalatest.funsuite.AnyFunSuite

class TempDirectoryTest extends AnyFunSuite {
  test("TempDirectory should create a directory when deleteAtExit is true") {
    val dir = TempDirectory.create(true)
    assert(dir.exists())
    assert(dir.isDirectory)
  }

  test("create a directory when deleteAtExit is false") {
    val dir = TempDirectory.create(false)
    assert(dir.exists())
    assert(dir.isDirectory)
  }
}
