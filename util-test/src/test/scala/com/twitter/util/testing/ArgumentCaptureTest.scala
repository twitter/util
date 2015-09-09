package com.twitter.util.testing

import org.junit.runner.RunWith
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar

@RunWith(classOf[JUnitRunner])
class ArgumentCaptureTest extends FunSuite with MockitoSugar with ArgumentCapture {
  class MockSubject {
    def method1(arg: String): Long = 0L
    def method2(arg1: String, arg2: String): Long = 0L
    def method3(arg1: String, arg2: String, arg3: String): Long = 0L
    def method4(arg1: String, arg2: String, arg3: String, arg4: String): Long = 0L
    def method5(arg1: String, arg2: String, arg3: String, arg4: String, arg5: String): Long = 0L
  }

  test("captureOne should handle 1-ary functions") {
    val theMockSubject = mock[MockSubject]
    when(theMockSubject.method1(any[String])).thenReturn(123L)

    assert(theMockSubject.method1("foo") === 123L)

    val captured = capturingOne(verify(theMockSubject).method1 _)
    assert(captured === "foo")
  }

  test("captureOne should handle 2-ary functions") {
    val theMockSubject = mock[MockSubject]
    when(theMockSubject.method2(any[String], any[String])).thenReturn(456L)

    assert(theMockSubject.method2("foo", "bar") === 456L)

    val captured = capturingOne(verify(theMockSubject).method2 _)
    assert(captured === (("foo", "bar")))
  }

  test("captureOne should handle 3-ary functions") {
    val theMockSubject = mock[MockSubject]
    when(theMockSubject.method3(any[String], any[String], any[String])).thenReturn(136L)

    assert(theMockSubject.method3("foo", "bar", "baz") === 136L)

    val captured = capturingOne(verify(theMockSubject).method3 _)
    assert(captured === (("foo", "bar", "baz")))
  }

  test("captureOne should handle 4-ary functions") {
    val theMockSubject = mock[MockSubject]
    when(theMockSubject.method4(any[String], any[String], any[String], any[String])).thenReturn(149L)

    assert(theMockSubject.method4("north", "east", "south", "west") === 149L)

    val captured = capturingOne(verify(theMockSubject).method4 _)
    assert(captured === (("north", "east", "south", "west")))
  }

  test("captureOne should handle 5-ary functions") {
    val theMockSubject = mock[MockSubject]
    when(theMockSubject.method5(any[String], any[String], any[String], any[String], any[String])).thenReturn(789L)

    assert(theMockSubject.method5("doh", "ray", "mi", "fa", "so") === 789L)

    val captured = capturingOne(verify(theMockSubject).method5 _)
    assert(captured === (("doh", "ray", "mi", "fa", "so")))
  }

  test("captureAll should handle 1-ary functions") {
    val theMockSubject = mock[MockSubject]
    when(theMockSubject.method1(any[String])).thenReturn(123L)
    assert(theMockSubject.method1("foo") === 123L)
    assert(theMockSubject.method1("bar") === 123L)

    val captured = capturingAll(verify(theMockSubject, times(2)).method1 _)
    assert(captured === Seq("foo", "bar"))
  }

  test("captureAll should handle 2-ary functions") {
    val theMockSubject = mock[MockSubject]
    when(theMockSubject.method2(any[String], any[String])).thenReturn(456L)
    assert(theMockSubject.method2("foo", "bar") === 456L)
    assert(theMockSubject.method2("baz", "spam") === 456L)

    val captured = capturingAll(verify(theMockSubject, times(2)).method2 _)
    assert(captured === Seq(("foo", "bar"), ("baz", "spam")))
  }

  test("captureAll should handle 3-ary functions") {
    val theMockSubject = mock[MockSubject]
    when(theMockSubject.method3(any[String], any[String], any[String])).thenReturn(136L)

    assert(theMockSubject.method3("foo", "bar", "baz") === 136L)
    assert(theMockSubject.method3("spam", "ham", "eggs") === 136L)

    val captured = capturingAll(verify(theMockSubject, times(2)).method3 _)
    assert(captured === Seq(
      ("foo", "bar", "baz"),
      ("spam", "ham", "eggs")
    ))
  }

  test("captureAll should handle 4-ary functions") {
    // This is really just a test of zip4
    val theMockSubject = mock[MockSubject]
    when(theMockSubject.method4(any[String], any[String], any[String], any[String])).thenReturn(149L)
    assert(theMockSubject.method4("foo", "bar", "baz", "spam") === 149L)
    assert(theMockSubject.method4("north", "east", "south", "west") === 149L)

    val captured = capturingAll(verify(theMockSubject, times(2)).method4 _)
    assert(captured === Seq(
      ("foo", "bar", "baz", "spam"),
      ("north", "east", "south", "west")
    ))
  }

  test("captureAll should handle 5-ary functions") {
    // This is really just a test of zip5
    val theMockSubject = mock[MockSubject]
    when(theMockSubject.method5(any[String], any[String], any[String], any[String], any[String])).thenReturn(789L)
    assert(theMockSubject.method5("foo", "bar", "baz", "spam", "ham") === 789L)
    assert(theMockSubject.method5("doh", "ray", "mi", "fa", "so") === 789L)

    val captured = capturingAll(verify(theMockSubject, times(2)).method5 _)
    assert(captured === Seq(
      ("foo", "bar", "baz", "spam", "ham"),
      ("doh", "ray", "mi", "fa", "so")
    ))
  }
}
