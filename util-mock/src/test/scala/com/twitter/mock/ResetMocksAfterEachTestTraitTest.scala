package com.twitter.mock

import org.junit.runner.RunWith
import org.mockito.MockitoSugar
import org.mockito.scalatest.ResetMocksAfterEachTest
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.junit.JUnitRunner

object ResetMocksAfterEachTestTraitTest {

  trait ReturnInteger {
    def get: Int
  }

  trait Foo {
    def bar(a: String) = "bar"
  }

  trait Baz {
    def qux(a: String) = "qux"
  }
}

/**
 * Ensure [[ResetMocksAfterEachTest]] works with [[com.twitter.mock.Mockito]]
 */
@RunWith(classOf[JUnitRunner])
class ResetMocksAfterEachTestTraitTest
    extends AnyFunSuite
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Mockito
    with ResetMocksAfterEachTest {
  import ResetMocksAfterEachTestTraitTest._

  val foo: Foo = mock[Foo]
  val baz: Baz = mock[Baz]

  private val mockReturnInteger: ReturnInteger = mock[ReturnInteger]
  mockReturnInteger.get returns 100

  override def beforeAll(): Unit = {
    mockReturnInteger.get should equal(100)
    super.beforeAll()
  }

  override def afterEach(): Unit = {
    super.afterEach()

    mockingDetails(mockReturnInteger).getInvocations.isEmpty should be(true)
  }

  test("no interactions 1") {
    MockitoSugar.verifyZeroInteractions(foo)

    foo.bar("bar") returns "mocked"
    foo.bar("bar") shouldBe "mocked"
  }

  test("no interactions 2") {
    MockitoSugar.verifyZeroInteractions(foo)

    foo.bar("bar") returns "mocked2"
    foo.bar("bar") shouldBe "mocked2"
  }

  test("no interactions for all 1") {
    MockitoSugar.verifyZeroInteractions(foo, baz)

    foo.bar("bar") returns "mocked3"
    baz.qux("qux") returns "mocked4"

    foo.bar("bar") shouldBe "mocked3"
    baz.qux("qux") shouldBe "mocked4"
  }

  test("no interactions for all 2") {
    MockitoSugar.verifyZeroInteractions(foo, baz)

    foo.bar("bar") returns "mocked5"
    baz.qux("qux") returns "mocked6"

    foo.bar("bar") shouldBe "mocked5"
    baz.qux("qux") shouldBe "mocked6"
  }
}
