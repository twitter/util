package com.twitter.mock

import org.junit.runner.RunWith
import org.mockito.exceptions.verification.{NeverWantedButInvoked, NoInteractionsWanted}
import org.scalatest.Outcome
import org.scalatest.funsuite.FixtureAnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MockitoTraitWithFixtureTest extends FixtureAnyFunSuite with Matchers with Mockito {
  class Foo {
    def bar(a: String) = "bar"
    def baz = "baz"
  }

  class FixtureParam {
    val foo: Foo = mock[Foo]
  }

  protected def withFixture(test: OneArgTest): Outcome = {
    val theFixture = new FixtureParam
    super.withFixture(test.toNoArgTest(theFixture))
  }

  test("verify no calls on fixture objects methods") { f: FixtureParam =>
    "mocked" willBe returned by f.foo.bar("jane")
    "mocked" willBe returned by f.foo.baz

    f.foo.bar("jane") should be("mocked")
    f.foo.baz should be("mocked")

    a[NeverWantedButInvoked] should be thrownBy {
      f.foo.bar(any[String]) wasNever called
    }
    a[NeverWantedButInvoked] should be thrownBy {
      f.foo.baz wasNever called
    }
  }

  test("verify no calls on a mock inside a fixture object") { f: FixtureParam =>
    f.foo.bar("jane") returns "mocked"
    f.foo wasNever called

    f.foo.bar("jane") should be("mocked")
    a[NoInteractionsWanted] should be thrownBy {
      f.foo wasNever called
    }
  }
}
