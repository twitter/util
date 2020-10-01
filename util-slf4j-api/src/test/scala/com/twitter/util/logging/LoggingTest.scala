package com.twitter.util.logging

import com.twitter.util.mock.Mockito
import org.mockito.Mockito.when
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.language.reflectiveCalls

class LoggingTest extends AnyFunSuite with Matchers with Mockito {

  /* Trace */

  test("Logging#trace enabled") {
    val f = fixture(_.isTraceEnabled, isEnabled = true)

    f.logger.trace(f.message)
    f.underlying.trace(f.message) was called
  }

  test("Logging#trace not enabled") {
    val f = fixture(_.isTraceEnabled, isEnabled = false)

    f.logger.trace(f.message)
    f.underlying.trace(any[String]) wasNever called
  }

  test("Logging#trace enabled with message and cause") {
    val f = fixture(_.isTraceEnabled, isEnabled = true)

    f.logger.trace(f.message, f.cause)
    f.underlying.trace(f.message, f.cause) was called
  }

  test("Logging#trace not enabled with message and cause") {
    val f = fixture(_.isTraceEnabled, isEnabled = false)

    f.logger.trace(f.message, f.cause)
    f.underlying.trace(any[String], any) wasNever called
  }

  test("Logging#trace enabled with parameters") {
    val f = fixture(_.isTraceEnabled, isEnabled = true)

    f.logger.traceWith(f.message, f.arg1)
    f.underlying.trace(f.message, Seq(f.arg1): _*) was called
    f.logger.traceWith(f.message, f.arg1, f.arg2)
    f.underlying.trace(f.message, Seq(f.arg1, f.arg2): _*) was called
    f.logger.traceWith(f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.trace(f.message, f.arg1, f.arg2, f.arg3) was called
  }

  test("Logging#trace not enabled with parameters") {
    val f = fixture(_.isTraceEnabled, isEnabled = false)

    f.logger.traceWith(f.message, f.arg1)
    f.underlying.trace(f.message, Seq(f.arg1): _*) wasNever called
    f.logger.traceWith(f.message, f.arg1, f.arg2)
    f.underlying.trace(f.message, Seq(f.arg1, f.arg2): _*) wasNever called
    f.logger.traceWith(f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.trace(f.message, f.arg1, f.arg2, f.arg3) wasNever called
  }

  /* Debug */

  test("Logging#debug enabled") {
    val f = fixture(_.isDebugEnabled, isEnabled = true)

    f.logger.debug(f.message)
    f.underlying.debug(f.message) was called
  }

  test("Logging#debug not enabled") {
    val f = fixture(_.isDebugEnabled, isEnabled = false)

    f.logger.debug(f.message)
    f.underlying.debug(any[String]) wasNever called
  }

  test("Logging#debug enabled with message and cause") {
    val f = fixture(_.isDebugEnabled, isEnabled = true)

    f.logger.debug(f.message, f.cause)
    f.underlying.debug(f.message, f.cause) was called
  }

  test("Logging#debug not enabled with message and cause") {
    val f = fixture(_.isDebugEnabled, isEnabled = false)

    f.logger.debug(f.message, f.cause)
    f.underlying.debug(any[String], any) wasNever called
  }

  test("Logging#debug enabled with parameters") {
    val f = fixture(_.isDebugEnabled, isEnabled = true)

    f.logger.debugWith(f.message, f.arg1)
    f.underlying.debug(f.message, Seq(f.arg1): _*) was called
    f.logger.debugWith(f.message, f.arg1, f.arg2)
    f.underlying.debug(f.message, Seq(f.arg1, f.arg2): _*) was called
    f.logger.debugWith(f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.debug(f.message, f.arg1, f.arg2, f.arg3) was called
  }

  test("Logging#debug not enabled with parameters") {
    val f = fixture(_.isDebugEnabled, isEnabled = false)

    f.logger.debugWith(f.message, f.arg1)
    f.underlying.debug(f.message, Seq(f.arg1): _*) wasNever called
    f.logger.debugWith(f.message, f.arg1, f.arg2)
    f.underlying.debug(f.message, Seq(f.arg1, f.arg2): _*) wasNever called
    f.logger.debugWith(f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.debug(f.message, f.arg1, f.arg2, f.arg3) wasNever called
  }

  /* Info */

  test("Logging#info enabled") {
    val f = fixture(_.isInfoEnabled, isEnabled = true)

    f.logger.info(f.message)
    f.underlying.info(f.message) was called
  }

  test("Logging#info not enabled") {
    val f = fixture(_.isInfoEnabled, isEnabled = false)

    f.logger.info(f.message)
    f.underlying.info(any[String]) wasNever called
  }

  test("Logging#info enabled with message and cause") {
    val f = fixture(_.isInfoEnabled, isEnabled = true)

    f.logger.info(f.message, f.cause)
    f.underlying.info(f.message, f.cause) was called
  }

  test("Logging#info not enabled with message and cause") {
    val f = fixture(_.isInfoEnabled, isEnabled = false)

    f.logger.info(f.message, f.cause)
    f.underlying.info(any[String], any) wasNever called
  }

  test("Logging#info enabled with parameters") {
    val f = fixture(_.isInfoEnabled, isEnabled = true)

    f.logger.infoWith(f.message, f.arg1)
    f.underlying.info(f.message, Seq(f.arg1): _*) was called
    f.logger.infoWith(f.message, f.arg1, f.arg2)
    f.underlying.info(f.message, Seq(f.arg1, f.arg2): _*) was called
    f.logger.infoWith(f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.info(f.message, f.arg1, f.arg2, f.arg3) was called
  }

  test("Logging#info not enabled with parameters") {
    val f = fixture(_.isInfoEnabled, isEnabled = false)

    f.logger.infoWith(f.message, f.arg1)
    f.underlying.info(f.message, Seq(f.arg1): _*) wasNever called
    f.logger.infoWith(f.message, f.arg1, f.arg2)
    f.underlying.info(f.message, Seq(f.arg1, f.arg2): _*) wasNever called
    f.logger.infoWith(f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.info(f.message, f.arg1, f.arg2, f.arg3) wasNever called
  }

  /* Warn */

  test("Logging#warn enabled") {
    val f = fixture(_.isWarnEnabled, isEnabled = true)

    f.logger.warn(f.message)
    f.underlying.warn(f.message) was called
  }

  test("Logging#warn not enabled") {
    val f = fixture(_.isWarnEnabled, isEnabled = false)

    f.logger.warn(f.message)
    f.underlying.warn(any[String]) wasNever called
  }

  test("Logging#warn enabled with message and cause") {
    val f = fixture(_.isWarnEnabled, isEnabled = true)

    f.logger.warn(f.message, f.cause)
    f.underlying.warn(f.message, f.cause) was called
  }

  test("Logging#warn not enabled with message and cause") {
    val f = fixture(_.isWarnEnabled, isEnabled = false)

    f.logger.warn(f.message, f.cause)
    f.underlying.warn(any[String], any) wasNever called
  }

  test("Logging#warn enabled with parameters") {
    val f = fixture(_.isWarnEnabled, isEnabled = true)

    f.logger.warnWith(f.message, f.arg1)
    f.underlying.warn(f.message, Seq(f.arg1): _*) was called
    f.logger.warnWith(f.message, f.arg1, f.arg2)
    f.underlying.warn(f.message, Seq(f.arg1, f.arg2): _*) was called
    f.logger.warnWith(f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.warn(f.message, f.arg1, f.arg2, f.arg3) was called
  }

  test("Logging#warn not enabled with parameters") {
    val f = fixture(_.isWarnEnabled, isEnabled = false)

    f.logger.warnWith(f.message, f.arg1)
    f.underlying.warn(f.message, Seq(f.arg1): _*) wasNever called
    f.logger.warnWith(f.message, f.arg1, f.arg2)
    f.underlying.warn(f.message, Seq(f.arg1, f.arg2): _*) wasNever called
    f.logger.warnWith(f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.warn(f.message, f.arg1, f.arg2, f.arg3) wasNever called
  }

  /* Error */

  test("Logging#error enabled") {
    val f = fixture(_.isErrorEnabled, isEnabled = true)

    f.logger.error(f.message)
    f.underlying.error(f.message) was called
  }

  test("Logging#error not enabled") {
    val f = fixture(_.isErrorEnabled, isEnabled = false)

    f.logger.error(f.message)
    f.underlying.error(any[String]) wasNever called
  }

  test("Logging#error enabled with message and cause") {
    val f = fixture(_.isErrorEnabled, isEnabled = true)

    f.logger.error(f.message, f.cause)
    f.underlying.error(f.message, f.cause) was called
  }

  test("Logging#error not enabled with message and cause") {
    val f = fixture(_.isErrorEnabled, isEnabled = false)

    f.logger.error(f.message, f.cause)
    f.underlying.error(any[String], any) wasNever called
  }

  test("Logging#error enabled with parameters") {
    val f = fixture(_.isErrorEnabled, isEnabled = true)

    f.logger.errorWith(f.message, f.arg1)
    f.underlying.error(f.message, Seq(f.arg1): _*) was called
    f.logger.errorWith(f.message, f.arg1, f.arg2)
    f.underlying.error(f.message, Seq(f.arg1, f.arg2): _*) was called
    f.logger.errorWith(f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.error(f.message, f.arg1, f.arg2, f.arg3) was called
  }

  test("Logging#error not enabled with parameters") {
    val f = fixture(_.isErrorEnabled, isEnabled = false)

    f.logger.errorWith(f.message, f.arg1)
    f.underlying.error(f.message, Seq(f.arg1): _*) wasNever called
    f.logger.errorWith(f.message, f.arg1, f.arg2)
    f.underlying.error(f.message, Seq(f.arg1, f.arg2): _*) wasNever called
    f.logger.errorWith(f.message, f.arg1, f.arg2, f.arg3)
    f.underlying.error(f.message, f.arg1, f.arg2, f.arg3) wasNever called
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
    val subItem = new ExtendedItem("product1 reissue", "Another shiny widget", 3)

    item.dimension
    subItem.dimension
  }

  test("Logging#Java class with new logger") {
    new TestJavaClass // INFO com.twitter.util.logging.TestJavaClass - Creating new TestJavaClass instance.

    val t = new TraitWithLogging {}
    t.myMethod1 // INFO com.twitter.util.logging.LoggingTest$$anon$1 - In myMethod1

    val je = new JavaExtension
    je.myMethod1 // INFO com.twitter.util.logging.JavaExtension - In myMethod1
    je.myMethod2() // INFO com.twitter.util.logging.JavaExtension - In myMethod2: using trait#info
    // INFO com.twitter.util.logging.JavaExtension - In myMethod2: using logger#info
    // INFO com.twitter.util.logging.JavaExtension - In myMethod2: using LOG#info
  }

  test("Logging#Java class that extends Scala class with Logging trait and new logger") {
    new Item("product1", "This is a great product", 3)
    new SecondItem("improved product1", "Another great product", 44, 3)
    new JavaItem("javaProduct1", "A shiny widget", 2, 11)
  }

  test("Logging#null values") {
    val logger = Logger(org.slf4j.LoggerFactory.getLogger(this.getClass))
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
    logger4.info("Logger4 name = " + logger4.name)

    val logger5 = Logger(ObjectWithLogging.getClass) // object
    logger5.info("Logger5 name = " + logger5.name)
  }

  /* Private */

  private def fixture(isEnabledFn: org.slf4j.Logger => Boolean, isEnabled: Boolean) = {
    new {
      val message = "msg"
      val cause = new RuntimeException("TEST EXCEPTION")
      val arg1 = "arg1"
      val arg2 = Integer.valueOf(1)
      val arg3 = "arg3"
      val underlying: org.slf4j.Logger = mock[org.slf4j.Logger]
      when(isEnabledFn(underlying)).thenReturn(isEnabled)
      val logger: Logger = Logger(underlying)
    }
  }
}
