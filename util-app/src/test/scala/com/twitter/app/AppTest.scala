package com.twitter.app

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

class TestApp(f: () => Unit) extends App {
  def main() = f()
}

@RunWith(classOf[JUnitRunner])
class AppTest extends FunSuite {
  test("App: propagate underlying exception from app") {
    val throwApp = new TestApp(() => throw new RuntimeException)

    intercept[RuntimeException] {
      throwApp.main(Array.empty[String])
    }
  }
}
