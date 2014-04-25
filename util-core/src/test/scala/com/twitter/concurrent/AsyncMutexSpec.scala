package com.twitter.concurrent

import com.twitter.util.Await

import org.junit.runner.RunWith

import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AsyncMutexSpec extends FlatSpec {
  "AsyncMutex" should  "admit only one operation at a time" in {
    val m = new AsyncMutex

    val a0 = m.acquire()
    val a1 = m.acquire()

    assert(a0.isDefined === true)
    assert(a1.isDefined === false)

    Await.result(a0).release()             // satisfy operation 0
    assert(a1.isDefined === true)   // 1 now available

    val a2 = m.acquire()
    assert(a2.isDefined === false)
    Await.result(a1).release()             // satisfy operation 1
    assert(a2.isDefined === true)
  }
}
