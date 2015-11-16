package com.twitter.util


import org.junit.runner.RunWith
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar

@RunWith(classOf[JUnitRunner])
class ConfigTest extends WordSpec with MockitoSugar with Matchers {
  import Config._

  "Config" should {
    "computed should delay evaluation" in {
      class Foo extends Config.Nothing {
        var didIt = false
        var x = 10
        var y = computed {
          didIt = true
          x * 2 + 5
        }
      }

      val foo = new Foo
      assert(foo.didIt == false)
      assert((foo.y: Int) == 25) // use type annotation to force implicit conversion
      assert(foo.didIt == true)
    }

    "subclass can override indepedent var for use in dependent var" in {
      class Foo extends Config.Nothing {
        var x = 10
        var y = computed(x * 2 + 5)
      }
      val bar = new Foo {
        x = 20
      }
      assert((bar.y: Int) == 45) // use type annotation to force implicit conversion
    }

    "missingValues" should {
      class Bar extends Config.Nothing {
        var z = required[Int]
      }
      class Baz extends Config.Nothing {
        var w = required[Int]
      }
      class Foo extends Config.Nothing {
        var x = required[Int]
        var y = 3
        var bar = required[Bar]
        var baz = optional[Baz]
      }

      "must return empty Seq when no values are missing" in {
        val foo = new Foo {
          x = 42
          bar = new Bar {
            z = 10
          }
        }
        assert(foo.missingValues == List())
      }

      "must find top-level missing values" in {
        val foo = new Foo
        assert(foo.missingValues.sorted == Seq("x", "bar").sorted)
      }

      "must find top-level and nested missing values" in {
        val foo = new Foo {
          bar = new Bar
        }
        assert(foo.missingValues.sorted == Seq("x", "bar.z").sorted)
      }

      "must find nested missing values in optional sub-configs" in {
        val foo = new Foo {
          x = 3
          bar = new Bar {
            z = 1
          }
          baz = new Baz
        }
        assert(foo.missingValues.sorted == Seq("baz.w").sorted)
      }
    }
  }
}
