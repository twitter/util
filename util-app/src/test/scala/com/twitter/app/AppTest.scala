package com.twitter.app

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

class TestApp(f: () => Unit) extends App {
  var reason: Option[String] = None
  protected override def exitOnError(reason: String) = {
    this.reason = Some(reason)
  }

  def main() = f()
}

@RunWith(classOf[JUnitRunner])
class AppTest extends FunSuite {
  test("App: propagate underlying exception from app") {
    val throwApp = new TestApp(() => throw new RuntimeException)

    intercept[RuntimeException] {
      throwApp.main(Array.empty)
    }
  }

  test("App: register on main call, last App wins") {
    val test1 = new TestApp(() => ())
    val test2 = new TestApp(() => ())

    assert(App.registered != Some(test1))
    assert(App.registered != Some(test2))

    test1.main(Array.empty)
    assert(App.registered === Some(test1))

    test2.main(Array.empty)
    assert(App.registered === Some(test2))
  }

  test("App: pass in bad args and expect usage") {
    val test1 = new TestApp(() => ())

    test1.main(Array("-environment=staging", "-environment=staging"))
    val theReason: String = test1.reason.getOrElse {
      fail("There should have been a usage printed and was not")
    }

    assert(theReason.contains("""Error parsing flag "environment""""))

  }
}
