package com.twitter.util.logging

import com.twitter.util.mock.Mockito
import org.mockito.Mockito.when
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.slf4j.Marker
import scala.language.reflectiveCalls

class MarkerLoggingTest extends AnyFunSuite with Matchers with Mockito {

  test("Marker Logging#trace enabled") {
    val f = fixture(_.isTraceEnabled(any[Marker]), isEnabled = true)

    f.logger.trace(f.marker, f.message)
    f.underlying.trace(f.marker, f.message) was called
  }

  test("Marker Logging#trace not enabled") {
    val f = fixture(_.isTraceEnabled(any[Marker]), isEnabled = false)

    f.logger.trace(f.marker, f.message)
    f.underlying.trace(any[Marker], any[String]) wasNever called
  }

  test("Marker Logging#trace enabled with message and cause") {
    val f = fixture(_.isTraceEnabled(any[Marker]), isEnabled = true)

    f.logger.trace(f.marker, f.message, f.cause)
    f.underlying.trace(f.marker, f.message, f.cause) was called
  }

  test("Marker Logging#trace not enabled with message and cause") {
    val f = fixture(_.isTraceEnabled(any[Marker]), isEnabled = false)

    f.logger.trace(f.marker, f.message, f.cause)
    f.underlying.trace(any[Marker], any[String], any) wasNever called
  }

  test("Marker Logging#trace enabled with parameters") {
    val f = fixture(_.isTraceEnabled(any[Marker]), isEnabled = true)

    f.logger.traceWith(f.marker, f.message, f.arg1)
    f.underlying.trace(f.marker, f.message, Seq(f.arg1): _*) was called
    f.logger.traceWith(f.marker, f.message, f.arg1, f.arg2)
    f.underlying.trace(f.marker, f.message, Seq(f.arg1, f.arg2): _*) was called
    f.logger.traceWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.trace(f.marker, f.message, f.arg1, f.arg2, f.arg3) was called
  }

  test("Marker Logging#trace not enabled with parameters") {
    val f = fixture(_.isTraceEnabled(any[Marker]), isEnabled = false)

    f.logger.traceWith(f.marker, f.message, f.arg1)
    f.underlying.trace(f.marker, f.message, Seq(f.arg1): _*) wasNever called
    f.logger.traceWith(f.marker, f.message, f.arg1, f.arg2)
    f.underlying.trace(f.marker, f.message, Seq(f.arg1, f.arg2): _*) wasNever called
    f.logger.traceWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.trace(f.marker, f.message, f.arg1, f.arg2, f.arg3) wasNever called
  }

  /* Debug */

  test("Marker Logging#debug enabled") {
    val f = fixture(_.isDebugEnabled(any[Marker]), isEnabled = true)

    f.logger.debug(f.marker, f.message)
    f.underlying.debug(f.marker, f.message) was called
  }

  test("Marker Logging#debug not enabled") {
    val f = fixture(_.isDebugEnabled(any[Marker]), isEnabled = false)

    f.logger.debug(f.marker, f.message)
    f.underlying.debug(any[Marker], any[String]) wasNever called
  }

  test("Marker Logging#debug enabled with message and cause") {
    val f = fixture(_.isDebugEnabled(any[Marker]), isEnabled = true)

    f.logger.debug(f.marker, f.message, f.cause)
    f.underlying.debug(f.marker, f.message, f.cause) was called
  }

  test("Marker Logging#debug not enabled with message and cause") {
    val f = fixture(_.isDebugEnabled(any[Marker]), isEnabled = false)

    f.logger.debug(f.marker, f.message, f.cause)
    f.underlying.debug(any[Marker], any[String], any) wasNever called
  }

  test("Marker Logging#debug enabled with parameters") {
    val f = fixture(_.isDebugEnabled(any[Marker]), isEnabled = true)

    f.logger.debugWith(f.marker, f.message, f.arg1)
    f.underlying.debug(f.marker, f.message, Seq(f.arg1): _*) was called
    f.logger.debugWith(f.marker, f.message, f.arg1, f.arg2)
    f.underlying.debug(f.marker, f.message, Seq(f.arg1, f.arg2): _*) was called
    f.logger.debugWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.debug(f.marker, f.message, f.arg1, f.arg2, f.arg3) was called
  }

  test("Marker Logging#debug not enabled with parameters") {
    val f = fixture(_.isDebugEnabled(any[Marker]), isEnabled = false)

    f.logger.debugWith(f.marker, f.message, f.arg1)
    f.underlying.debug(f.marker, f.message, Seq(f.arg1): _*) wasNever called
    f.logger.debugWith(f.marker, f.message, f.arg1, f.arg2)
    f.underlying.debug(f.marker, f.message, Seq(f.arg1, f.arg2): _*) wasNever called
    f.logger.debugWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.debug(f.marker, f.message, f.arg1, f.arg2, f.arg3) wasNever called
  }

  /* Info */

  test("Marker Logging#info enabled") {
    val f = fixture(_.isInfoEnabled(any[Marker]), isEnabled = true)

    f.logger.info(f.marker, f.message)
    f.underlying.info(f.marker, f.message) was called
  }

  test("Marker Logging#info not enabled") {
    val f = fixture(_.isInfoEnabled(any[Marker]), isEnabled = false)

    f.logger.info(f.marker, f.message)
    f.underlying.info(any[Marker], any[String]) wasNever called
  }

  test("Marker Logging#info enabled with message and cause") {
    val f = fixture(_.isInfoEnabled(any[Marker]), isEnabled = true)

    f.logger.info(f.marker, f.message, f.cause)
    f.underlying.info(f.marker, f.message, f.cause) was called
  }

  test("Marker Logging#info not enabled with message and cause") {
    val f = fixture(_.isInfoEnabled(any[Marker]), isEnabled = false)

    f.logger.info(f.marker, f.message, f.cause)
    f.underlying.info(any[Marker], any[String], any) wasNever called
  }

  test("Marker Logging#info enabled with parameters") {
    val f = fixture(_.isInfoEnabled(any[Marker]), isEnabled = true)

    f.logger.infoWith(f.marker, f.message, f.arg1)
    f.underlying.info(f.marker, f.message, Seq(f.arg1): _*) was called
    f.logger.infoWith(f.marker, f.message, f.arg1, f.arg2)
    f.underlying.info(f.marker, f.message, Seq(f.arg1, f.arg2): _*) was called
    f.logger.infoWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.info(f.marker, f.message, f.arg1, f.arg2, f.arg3) was called
  }

  test("Marker Logging#info not enabled with parameters") {
    val f = fixture(_.isInfoEnabled(any[Marker]), isEnabled = false)

    f.logger.infoWith(f.marker, f.message, f.arg1)
    f.underlying.info(f.marker, f.message, Seq(f.arg1): _*) wasNever called
    f.logger.infoWith(f.marker, f.message, f.arg1, f.arg2)
    f.underlying.info(f.marker, f.message, Seq(f.arg1, f.arg2): _*) wasNever called
    f.logger.infoWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.info(f.marker, f.message, f.arg1, f.arg2, f.arg3) wasNever called
  }

  /* Warn */

  test("Marker Logging#warn enabled") {
    val f = fixture(_.isWarnEnabled(any[Marker]), isEnabled = true)

    f.logger.warn(f.marker, f.message)
    f.underlying.warn(f.marker, f.message) was called
  }

  test("Marker Logging#warn not enabled") {
    val f = fixture(_.isWarnEnabled(any[Marker]), isEnabled = false)

    f.logger.warn(f.marker, f.message)
    f.underlying.warn(any[Marker], any[String]) wasNever called
  }

  test("Marker Logging#warn enabled with message and cause") {
    val f = fixture(_.isWarnEnabled(any[Marker]), isEnabled = true)

    f.logger.warn(f.marker, f.message, f.cause)
    f.underlying.warn(f.marker, f.message, f.cause) was called
  }

  test("Marker Logging#warn not enabled with message and cause") {
    val f = fixture(_.isWarnEnabled(any[Marker]), isEnabled = false)

    f.logger.warn(f.marker, f.message, f.cause)
    f.underlying.warn(any[Marker], any[String], any) wasNever called
  }

  test("Marker Logging#warn enabled with parameters") {
    val f = fixture(_.isWarnEnabled(any[Marker]), isEnabled = true)

    f.logger.warnWith(f.marker, f.message, f.arg1)
    f.underlying.warn(f.marker, f.message, Seq(f.arg1): _*) was called
    f.logger.warnWith(f.marker, f.message, f.arg1, f.arg2)
    f.underlying.warn(f.marker, f.message, Seq(f.arg1, f.arg2): _*) was called
    f.logger.warnWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.warn(f.marker, f.message, f.arg1, f.arg2, f.arg3) was called
  }

  test("Marker Logging#warn not enabled with parameters") {
    val f = fixture(_.isWarnEnabled(any[Marker]), isEnabled = false)

    f.logger.warnWith(f.marker, f.message, f.arg1)
    f.underlying.warn(f.marker, f.message, Seq(f.arg1): _*) wasNever called
    f.logger.warnWith(f.marker, f.message, f.arg1, f.arg2)
    f.underlying.warn(f.marker, f.message, Seq(f.arg1, f.arg2): _*) wasNever called
    f.logger.warnWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.warn(f.marker, f.message, f.arg1, f.arg2, f.arg3) wasNever called
  }

  /* Error */

  test("Marker Logging#error enabled") {
    val f = fixture(_.isErrorEnabled(any[Marker]), isEnabled = true)

    f.logger.error(f.marker, f.message)
    f.underlying.error(f.marker, f.message) was called
  }

  test("Marker Logging#error not enabled") {
    val f = fixture(_.isErrorEnabled(any[Marker]), isEnabled = false)

    f.logger.error(f.marker, f.message)
    f.underlying.error(any[Marker], any[String]) wasNever called
  }

  test("Marker Logging#error enabled with message and cause") {
    val f = fixture(_.isErrorEnabled(any[Marker]), isEnabled = true)

    f.logger.error(f.marker, f.message, f.cause)
    f.underlying.error(f.marker, f.message, f.cause) was called
  }

  test("Marker Logging#error not enabled with message and cause") {
    val f = fixture(_.isErrorEnabled(any[Marker]), isEnabled = false)

    f.logger.error(f.marker, f.message, f.cause)
    f.underlying.error(any[Marker], any[String], any) wasNever called
  }

  test("Marker Logging#error enabled with parameters") {
    val f = fixture(_.isErrorEnabled(any[Marker]), isEnabled = true)

    f.logger.errorWith(f.marker, f.message, f.arg1)
    f.underlying.error(f.marker, f.message, Seq(f.arg1): _*) was called
    f.logger.errorWith(f.marker, f.message, f.arg1, f.arg2)
    f.underlying.error(f.marker, f.message, Seq(f.arg1, f.arg2): _*) was called
    f.logger.errorWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.error(f.marker, f.message, f.arg1, f.arg2, f.arg3) was called
  }

  test("Marker Logging#error not enabled with parameters") {
    val f = fixture(_.isErrorEnabled(any[Marker]), isEnabled = false)

    f.logger.errorWith(f.marker, f.message, f.arg1)
    f.underlying.error(f.marker, f.message, Seq(f.arg1): _*) wasNever called
    f.logger.errorWith(f.marker, f.message, f.arg1, f.arg2)
    f.underlying.error(f.marker, f.message, Seq(f.arg1, f.arg2): _*) wasNever called
    f.logger.errorWith(f.marker, f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.error(f.marker, f.message, f.arg1, f.arg2, f.arg3) wasNever called
  }

  /* Private */

  private def fixture(p: org.slf4j.Logger => Boolean, isEnabled: Boolean) = new {
    val marker: Marker = TestMarker
    val message = "msg"
    val cause = new RuntimeException("TEST EXCEPTION")
    val arg1 = "arg1"
    val arg2 = new Integer(1)
    val arg3 = "arg3"
    val underlying: org.slf4j.Logger = mock[org.slf4j.Logger]
    when(p(underlying)).thenReturn(isEnabled)
    val logger: Logger = Logger(underlying)
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
