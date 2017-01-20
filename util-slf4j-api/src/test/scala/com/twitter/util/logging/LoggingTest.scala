package com.twitter.util.logging

import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import org.slf4j
import org.slf4j.LoggerFactory
import scala.language.reflectiveCalls

class LoggingTest
  extends FunSuite
  with Matchers
  with MockitoSugar {

  /* Trace */

  test("Logging#trace enabled") {
    val f = fixture(_.isTraceEnabled, isEnabled = true)

    f.logger.trace(f.message)
    verify(f.underlying).trace(f.message)
  }

  test("Logging#trace not enabled") {
    val f = fixture(_.isTraceEnabled, isEnabled = false)

    f.logger.trace(f.message)
    verify(f.underlying, never).trace(anyString)
  }

  test("Logging#trace enabled with message and cause") {
    val f = fixture(_.isTraceEnabled, isEnabled = true)

    f.logger.trace(f.message, f.cause)
    verify(f.underlying).trace(f.message, f.cause)
  }

  test("Logging#trace not enabled with message and cause") {
    val f = fixture(_.isTraceEnabled, isEnabled = false)

    f.logger.trace(f.message, f.cause)
    verify(f.underlying, never).trace(anyString, anyObject)
  }

  test("Logging#trace enabled with parameters") {
    val f = fixture(_.isTraceEnabled, isEnabled = true)

    f.logger.traceWith(f.message, f.arg1)
    verify(f.underlying).trace(f.message, Seq(f.arg1): _*)
    f.logger.traceWith(f.message, f.arg1, f.arg2)
    verify(f.underlying).trace(f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.traceWith(f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying).trace(f.message, f.arg1, f.arg2, f.arg3)
  }

  test("Logging#trace not enabled with parameters") {
    val f = fixture(_.isTraceEnabled, isEnabled = false)

    f.logger.traceWith(f.message, f.arg1)
    verify(f.underlying, never).trace(f.message, Seq(f.arg1): _*)
    f.logger.traceWith(f.message, f.arg1, f.arg2)
    verify(f.underlying, never).trace(f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.traceWith(f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying, never).trace(f.message, f.arg1, f.arg2, f.arg3)
  }

  /* Debug */

  test("Logging#debug enabled") {
    val f = fixture(_.isDebugEnabled, isEnabled = true)

    f.logger.debug(f.message)
    verify(f.underlying).debug(f.message)
  }

  test("Logging#debug not enabled") {
    val f = fixture(_.isDebugEnabled, isEnabled = false)

    f.logger.debug(f.message)
    verify(f.underlying, never).debug(anyString)
  }

  test("Logging#debug enabled with message and cause") {
    val f = fixture(_.isDebugEnabled, isEnabled = true)

    f.logger.debug(f.message, f.cause)
    verify(f.underlying).debug(f.message, f.cause)
  }

  test("Logging#debug not enabled with message and cause") {
    val f = fixture(_.isDebugEnabled, isEnabled = false)

    f.logger.debug(f.message, f.cause)
    verify(f.underlying, never).debug(anyString, anyObject)
  }

  test("Logging#debug enabled with parameters") {
    val f = fixture(_.isDebugEnabled, isEnabled = true)

    f.logger.debugWith(f.message, f.arg1)
    verify(f.underlying).debug(f.message, Seq(f.arg1): _*)
    f.logger.debugWith(f.message, f.arg1, f.arg2)
    verify(f.underlying).debug(f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.debugWith(f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying).debug(f.message, f.arg1, f.arg2, f.arg3)
  }

  test("Logging#debug not enabled with parameters") {
    val f = fixture(_.isDebugEnabled, isEnabled = false)

    f.logger.debugWith(f.message, f.arg1)
    verify(f.underlying, never).debug(f.message, Seq(f.arg1): _*)
    f.logger.debugWith(f.message, f.arg1, f.arg2)
    verify(f.underlying, never).debug(f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.debugWith(f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying, never).debug(f.message, f.arg1, f.arg2, f.arg3)
  }


  /* Info */

  test("Logging#info enabled") {
    val f = fixture(_.isInfoEnabled, isEnabled = true)

    f.logger.info(f.message)
    verify(f.underlying).info(f.message)
  }

  test("Logging#info not enabled") {
    val f = fixture(_.isInfoEnabled, isEnabled = false)

    f.logger.info(f.message)
    verify(f.underlying, never).info(anyString)
  }

  test("Logging#info enabled with message and cause") {
    val f = fixture(_.isInfoEnabled, isEnabled = true)

    f.logger.info(f.message, f.cause)
    verify(f.underlying).info(f.message, f.cause)
  }

  test("Logging#info not enabled with message and cause") {
    val f = fixture(_.isInfoEnabled, isEnabled = false)

    f.logger.info(f.message, f.cause)
    verify(f.underlying, never).info(anyString, anyObject)
  }

  test("Logging#info enabled with parameters") {
    val f = fixture(_.isInfoEnabled, isEnabled = true)

    f.logger.infoWith(f.message, f.arg1)
    verify(f.underlying).info(f.message, Seq(f.arg1): _*)
    f.logger.infoWith(f.message, f.arg1, f.arg2)
    verify(f.underlying).info(f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.infoWith(f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying).info(f.message, f.arg1, f.arg2, f.arg3)
  }

  test("Logging#info not enabled with parameters") {
    val f = fixture(_.isInfoEnabled, isEnabled = false)

    f.logger.infoWith(f.message, f.arg1)
    verify(f.underlying, never).info(f.message, Seq(f.arg1): _*)
    f.logger.infoWith(f.message, f.arg1, f.arg2)
    verify(f.underlying, never).info(f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.infoWith(f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying, never).info(f.message, f.arg1, f.arg2, f.arg3)
  }

  /* Warn */

  test("Logging#warn enabled") {
    val f = fixture(_.isWarnEnabled, isEnabled = true)

    f.logger.warn(f.message)
    verify(f.underlying).warn(f.message)
  }

  test("Logging#warn not enabled") {
    val f = fixture(_.isWarnEnabled, isEnabled = false)

    f.logger.warn(f.message)
    verify(f.underlying, never).warn(anyString)
  }

  test("Logging#warn enabled with message and cause") {
    val f = fixture(_.isWarnEnabled, isEnabled = true)

    f.logger.warn(f.message, f.cause)
    verify(f.underlying).warn(f.message, f.cause)
  }

  test("Logging#warn not enabled with message and cause") {
    val f = fixture(_.isWarnEnabled, isEnabled = false)

    f.logger.warn(f.message, f.cause)
    verify(f.underlying, never).warn(anyString, anyObject)
  }

  test("Logging#warn enabled with parameters") {
    val f = fixture(_.isWarnEnabled, isEnabled = true)

    f.logger.warnWith(f.message, f.arg1)
    verify(f.underlying).warn(f.message, Seq(f.arg1): _*)
    f.logger.warnWith(f.message, f.arg1, f.arg2)
    verify(f.underlying).warn(f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.warnWith(f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying).warn(f.message, f.arg1, f.arg2, f.arg3)
  }

  test("Logging#warn not enabled with parameters") {
    val f = fixture(_.isWarnEnabled, isEnabled = false)

    f.logger.warnWith(f.message, f.arg1)
    verify(f.underlying, never).warn(f.message, Seq(f.arg1): _*)
    f.logger.warnWith(f.message, f.arg1, f.arg2)
    verify(f.underlying, never).warn(f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.warnWith(f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying, never).warn(f.message, f.arg1, f.arg2, f.arg3)
  }

  /* Error */

  test("Logging#error enabled") {
    val f = fixture(_.isErrorEnabled, isEnabled = true)

    f.logger.error(f.message)
    verify(f.underlying).error(f.message)
  }

  test("Logging#error not enabled") {
    val f = fixture(_.isErrorEnabled, isEnabled = false)

    f.logger.error(f.message)
    verify(f.underlying, never).error(anyString)
  }

  test("Logging#error enabled with message and cause") {
    val f = fixture(_.isErrorEnabled, isEnabled = true)

    f.logger.error(f.message, f.cause)
    verify(f.underlying).error(f.message, f.cause)
  }

  test("Logging#error not enabled with message and cause") {
    val f = fixture(_.isErrorEnabled, isEnabled = false)

    f.logger.error(f.message, f.cause)
    verify(f.underlying, never).error(anyString, anyObject)
  }

  test("Logging#error enabled with parameters") {
    val f = fixture(_.isErrorEnabled, isEnabled = true)

    f.logger.errorWith(f.message, f.arg1)
    verify(f.underlying).error(f.message, Seq(f.arg1): _*)
    f.logger.errorWith(f.message, f.arg1, f.arg2)
    verify(f.underlying).error(f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.errorWith(f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying).error(f.message, f.arg1, f.arg2, f.arg3)
  }

  test("Logging#error not enabled with parameters") {
    val f = fixture(_.isErrorEnabled, isEnabled = false)

    f.logger.errorWith(f.message, f.arg1)
    verify(f.underlying, never).error(f.message, Seq(f.arg1): _*)
    f.logger.errorWith(f.message, f.arg1, f.arg2)
    verify(f.underlying, never).error(f.message, Seq(f.arg1, f.arg2): _*)
    f.logger.errorWith(f.message, f.arg1, f.arg2, f.arg3)
    verify(f.underlying, never).error(f.message, f.arg1, f.arg2, f.arg3)
  }

  /* Instance Logging */

  test("Logging#case classes") {
    val o = CaseClassWithLogging(3.0, 4.5, 23.4)
    o.area
  }

  test("Logging#classes") {
    val item1 = new Item("product1", "A shiny widget", -2)
    val item2 = new Item("product2", "A less shiny widget", 25)

    item1.dimension
    item2.dimension

    item1.foo
    item2.foo

    item1.bar
    item2.bar

    val stock = new Stock("ASDF", 100.0)
    stock.quote

    ObjectWithLogging.interesting

    val fruit = new Fruit("apple", "red", 1.49)
    fruit.description
  }

  test("Logging#extended class extends Logging") {
    val item = new Item("product1", "A shiny widget", -2)
    val subItem = new ExtendedItem("product1 reissue",  "Another shiny widget", 3)

    item.dimension
    subItem.dimension
  }

  test("Logging#Java class with new logger") {
    new TestJavaClass()
  }

  test("Logging#Java class that extends Scala class with Logging trait and new logger") {
    new Item("product1", "This is a great product", 3)
    new SecondItem("improved product1", "Another great product", 44, 3)
    new JavaItem("javaProduct1", "A shiny widget", 2, 11)
  }

  test("Logging#null values") {
    val logger = Logger(LoggerFactory.getLogger(this.getClass))
    logger.info(null)
    val list = Nil
    logger.info(list.toString)
    logger.info(s"${Option(null)}")

    logger.error(null)
    logger.error(s"${Option(null)}")

    val clazz = CaseClassWithLogging(3.0, 4.0, 5.0)
    clazz.logNulls
  }

  test("Logger#class constructors") {
    val logger1 = Logger(classOf[TestSerializable])
    logger1.info("Logger1 name = " + logger1.name)

    val logger2 = Logger[TestSerializable]
    logger2.info("Logger2 name = " + logger2.name)

    val logger3 = Logger(new TestSerializable(5, 4).getClass)
    logger3.info("Logger3 name = " + logger3.name)

    val logger4 = Logger[Item] // has companion object
    logger4.info("Logger4 name = "+ logger4.name)

    val logger5 = Logger(ObjectWithLogging.getClass) // object
    logger5.info("Logger5 name = " + logger5.name)
  }

  /* Private */

  private def fixture(
    isEnabledFn: slf4j.Logger => Boolean,
    isEnabled: Boolean) = {
    new {
      val message = "msg"
      val cause = new RuntimeException("TEST EXCEPTION")
      val arg1 = "arg1"
      val arg2 = new Integer(1)
      val arg3 = "arg3"
      val underlying = mock[org.slf4j.Logger]
      when(isEnabledFn(underlying)).thenReturn(isEnabled)
      val logger = Logger(underlying)
    }
  }
}

