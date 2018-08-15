package com.twitter.util.logging

import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import org.slf4j
import org.slf4j.Marker
import scala.language.reflectiveCalls

class MarkerLoggingTest extends FunSuite with Matchers with MockitoSugar {

  test("Marker Logging#trace enabled") {
    val f = fixture(_.isTraceEnabled(anyObject[Marker]), isEnabled = true)

    f.logger.trace(f.marker, f.message)
    verify(f.underlying).trace(f.marker, f.message)
  }

  test("Marker Logging#trace not enabled") {
    val f = fixture(_.isTraceEnabled(anyObject[Marker]), isEnabled = false)

    f.logger.trace(f.marker, f.message)
    verify(f.underlying, never).trace(anyObject[Marker], anyString)
  }

  test("Marker Logging#trace enabled with message and cause") {
    val f = fixture(_.isTraceEnabled(anyObject[Marker]), isEnabled = true)

    f.logger.trace(f.marker, f.message, f.cause)
    verify(f.underlying).trace(f.marker, f.message, f.cause)
  }

  test("Marker Logging#trace not enabled with message and cause") {
    val f = fixture(_.isTraceEnabled(anyObject[Marker]), isEnabled = false)

    f.logger.trace(f.marker, f.message, f.cause)
    verify(f.underlying, never).trace(anyObject[Marker], anyString, anyObject)
  }

  test("Marker Logging#trace enabled with parameters") {
    val f = fixture(_.isTraceEnabled(anyObject[Marker]), isEnabled = true)

    f.logger.traceWith(f.marker, f.message, f.arg1)
    verify(f.underlying).trace(f.marker, f.message, Seq(f.arg1): _*)
    f.logger.traceWith(f.marker, f.message, f.arg1, f.arg2)
    verify(f.underlying).trace(f.marker, f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.traceWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying).trace(f.marker, f.message, f.arg1, f.arg2, f.arg3)
  }

  test("Marker Logging#trace not enabled with parameters") {
    val f = fixture(_.isTraceEnabled(anyObject[Marker]), isEnabled = false)

    f.logger.traceWith(f.marker, f.message, f.arg1)
    verify(f.underlying, never).trace(f.marker, f.message, Seq(f.arg1): _*)
    f.logger.traceWith(f.marker, f.message, f.arg1, f.arg2)
    verify(f.underlying, never).trace(f.marker, f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.traceWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying, never).trace(f.marker, f.message, f.arg1, f.arg2, f.arg3)
  }

  /* Debug */

  test("Marker Logging#debug enabled") {
    val f = fixture(_.isDebugEnabled(anyObject[Marker]), isEnabled = true)

    f.logger.debug(f.marker, f.message)
    verify(f.underlying).debug(f.marker, f.message)
  }

  test("Marker Logging#debug not enabled") {
    val f = fixture(_.isDebugEnabled(anyObject[Marker]), isEnabled = false)

    f.logger.debug(f.marker, f.message)
    verify(f.underlying, never).debug(anyObject[Marker], anyString)
  }

  test("Marker Logging#debug enabled with message and cause") {
    val f = fixture(_.isDebugEnabled(anyObject[Marker]), isEnabled = true)

    f.logger.debug(f.marker, f.message, f.cause)
    verify(f.underlying).debug(f.marker, f.message, f.cause)
  }

  test("Marker Logging#debug not enabled with message and cause") {
    val f = fixture(_.isDebugEnabled(anyObject[Marker]), isEnabled = false)

    f.logger.debug(f.marker, f.message, f.cause)
    verify(f.underlying, never).debug(anyObject[Marker], anyString, anyObject)
  }

  test("Marker Logging#debug enabled with parameters") {
    val f = fixture(_.isDebugEnabled(anyObject[Marker]), isEnabled = true)

    f.logger.debugWith(f.marker, f.message, f.arg1)
    verify(f.underlying).debug(f.marker, f.message, Seq(f.arg1): _*)
    f.logger.debugWith(f.marker, f.message, f.arg1, f.arg2)
    verify(f.underlying).debug(f.marker, f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.debugWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying).debug(f.marker, f.message, f.arg1, f.arg2, f.arg3)
  }

  test("Marker Logging#debug not enabled with parameters") {
    val f = fixture(_.isDebugEnabled(anyObject[Marker]), isEnabled = false)

    f.logger.debugWith(f.marker, f.message, f.arg1)
    verify(f.underlying, never).debug(f.marker, f.message, Seq(f.arg1): _*)
    f.logger.debugWith(f.marker, f.message, f.arg1, f.arg2)
    verify(f.underlying, never).debug(f.marker, f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.debugWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying, never).debug(f.marker, f.message, f.arg1, f.arg2, f.arg3)
  }

  /* Info */

  test("Marker Logging#info enabled") {
    val f = fixture(_.isInfoEnabled(anyObject[Marker]), isEnabled = true)

    f.logger.info(f.marker, f.message)
    verify(f.underlying).info(f.marker, f.message)
  }

  test("Marker Logging#info not enabled") {
    val f = fixture(_.isInfoEnabled(anyObject[Marker]), isEnabled = false)

    f.logger.info(f.marker, f.message)
    verify(f.underlying, never).info(anyObject[Marker], anyString)
  }

  test("Marker Logging#info enabled with message and cause") {
    val f = fixture(_.isInfoEnabled(anyObject[Marker]), isEnabled = true)

    f.logger.info(f.marker, f.message, f.cause)
    verify(f.underlying).info(f.marker, f.message, f.cause)
  }

  test("Marker Logging#info not enabled with message and cause") {
    val f = fixture(_.isInfoEnabled(anyObject[Marker]), isEnabled = false)

    f.logger.info(f.marker, f.message, f.cause)
    verify(f.underlying, never).info(anyObject[Marker], anyString, anyObject)
  }

  test("Marker Logging#info enabled with parameters") {
    val f = fixture(_.isInfoEnabled(anyObject[Marker]), isEnabled = true)

    f.logger.infoWith(f.marker, f.message, f.arg1)
    verify(f.underlying).info(f.marker, f.message, Seq(f.arg1): _*)
    f.logger.infoWith(f.marker, f.message, f.arg1, f.arg2)
    verify(f.underlying).info(f.marker, f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.infoWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying).info(f.marker, f.message, f.arg1, f.arg2, f.arg3)
  }

  test("Marker Logging#info not enabled with parameters") {
    val f = fixture(_.isInfoEnabled(anyObject[Marker]), isEnabled = false)

    f.logger.infoWith(f.marker, f.message, f.arg1)
    verify(f.underlying, never).info(f.marker, f.message, Seq(f.arg1): _*)
    f.logger.infoWith(f.marker, f.message, f.arg1, f.arg2)
    verify(f.underlying, never).info(f.marker, f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.infoWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying, never).info(f.marker, f.message, f.arg1, f.arg2, f.arg3)
  }

  /* Warn */

  test("Marker Logging#warn enabled") {
    val f = fixture(_.isWarnEnabled(anyObject[Marker]), isEnabled = true)

    f.logger.warn(f.marker, f.message)
    verify(f.underlying).warn(f.marker, f.message)
  }

  test("Marker Logging#warn not enabled") {
    val f = fixture(_.isWarnEnabled(anyObject[Marker]), isEnabled = false)

    f.logger.warn(f.marker, f.message)
    verify(f.underlying, never).warn(anyObject[Marker], anyString)
  }

  test("Marker Logging#warn enabled with message and cause") {
    val f = fixture(_.isWarnEnabled(anyObject[Marker]), isEnabled = true)

    f.logger.warn(f.marker, f.message, f.cause)
    verify(f.underlying).warn(f.marker, f.message, f.cause)
  }

  test("Marker Logging#warn not enabled with message and cause") {
    val f = fixture(_.isWarnEnabled(anyObject[Marker]), isEnabled = false)

    f.logger.warn(f.marker, f.message, f.cause)
    verify(f.underlying, never).warn(anyObject[Marker], anyString, anyObject)
  }

  test("Marker Logging#warn enabled with parameters") {
    val f = fixture(_.isWarnEnabled(anyObject[Marker]), isEnabled = true)

    f.logger.warnWith(f.marker, f.message, f.arg1)
    verify(f.underlying).warn(f.marker, f.message, Seq(f.arg1): _*)
    f.logger.warnWith(f.marker, f.message, f.arg1, f.arg2)
    verify(f.underlying).warn(f.marker, f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.warnWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying).warn(f.marker, f.message, f.arg1, f.arg2, f.arg3)
  }

  test("Marker Logging#warn not enabled with parameters") {
    val f = fixture(_.isWarnEnabled(anyObject[Marker]), isEnabled = false)

    f.logger.warnWith(f.marker, f.message, f.arg1)
    verify(f.underlying, never).warn(f.marker, f.message, Seq(f.arg1): _*)
    f.logger.warnWith(f.marker, f.message, f.arg1, f.arg2)
    verify(f.underlying, never).warn(f.marker, f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.warnWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying, never).warn(f.marker, f.message, f.arg1, f.arg2, f.arg3)
  }

  /* Error */

  test("Marker Logging#error enabled") {
    val f = fixture(_.isErrorEnabled(anyObject[Marker]), isEnabled = true)

    f.logger.error(f.marker, f.message)
    verify(f.underlying).error(f.marker, f.message)
  }

  test("Marker Logging#error not enabled") {
    val f = fixture(_.isErrorEnabled(anyObject[Marker]), isEnabled = false)

    f.logger.error(f.marker, f.message)
    verify(f.underlying, never).error(anyObject[Marker], anyString)
  }

  test("Marker Logging#error enabled with message and cause") {
    val f = fixture(_.isErrorEnabled(anyObject[Marker]), isEnabled = true)

    f.logger.error(f.marker, f.message, f.cause)
    verify(f.underlying).error(f.marker, f.message, f.cause)
  }

  test("Marker Logging#error not enabled with message and cause") {
    val f = fixture(_.isErrorEnabled(anyObject[Marker]), isEnabled = false)

    f.logger.error(f.marker, f.message, f.cause)
    verify(f.underlying, never).error(anyObject[Marker], anyString, anyObject)
  }

  test("Marker Logging#error enabled with parameters") {
    val f = fixture(_.isErrorEnabled(anyObject[Marker]), isEnabled = true)

    f.logger.errorWith(f.marker, f.message, f.arg1)
    verify(f.underlying).error(f.marker, f.message, Seq(f.arg1): _*)
    f.logger.errorWith(f.marker, f.message, f.arg1, f.arg2)
    verify(f.underlying).error(f.marker, f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.errorWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying).error(f.marker, f.message, f.arg1, f.arg2, f.arg3)
  }

  test("Marker Logging#error not enabled with parameters") {
    val f = fixture(_.isErrorEnabled(anyObject[Marker]), isEnabled = false)

    f.logger.errorWith(f.marker, f.message, f.arg1)
    verify(f.underlying, never).error(f.marker, f.message, Seq(f.arg1): _*)
    f.logger.errorWith(f.marker, f.message, f.arg1, f.arg2)
    verify(f.underlying, never).error(f.marker, f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.errorWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying, never).error(f.marker, f.message, f.arg1, f.arg2, f.arg3)
  }

  /* Private */

  private def fixture(p: slf4j.Logger => Boolean, isEnabled: Boolean) = {
    new {
      val marker: Marker = TestMarker
      val message = "msg"
      val cause = new RuntimeException("TEST EXCEPTION")
      val arg1 = "arg1"
      val arg2 = new Integer(1)
      val arg3 = "arg3"
      val underlying: org.slf4j.Logger = mock[org.slf4j.Logger]
      when(p(underlying)).thenReturn(isEnabled)
      val logger = Logger(underlying)
    }
  }

  object TestMarker extends Marker {
    def add(childMarker: Marker): Unit = {}
    def contains(childName: String): Boolean = false
    def contains(child: Marker): Boolean = false
    def getName: String = "TestMarker"
    def hasChildren: Boolean = false
    def hasReferences: Boolean = false
    def iterator(): java.util.Iterator[Marker] = new java.util.Iterator[Marker] {
      def hasNext: Boolean = false
      def next(): Marker = throw new NoSuchElementException()
      override def remove(): Unit = throw new NoSuchElementException()
    }
    def remove(child: Marker): Boolean = false
  }

}
