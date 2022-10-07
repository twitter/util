package com.twitter.util

import org.scalatest.funsuite.AnyFunSuite

class LastWriteWinsQueueTest extends AnyFunSuite {
  val queue = new LastWriteWinsQueue[String]

  test("LastWriteWinsQueue should add & remove items") {
    assert(queue.size == 0)
    queue.add("1")
    assert(queue.size == 1)
    assert(queue.remove == "1")
    assert(queue.size == 0)
  }

  test("LastWriteWinsQueue should last write wins") {
    queue.add("1")
    queue.add("2")
    assert(queue.size == 1)
    assert(queue.poll() == "2")
    assert(queue.size == 0)
    assert(queue.poll() == null)
  }
}
