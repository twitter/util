package com.twitter.util


import org.scalatest.{WordSpec, Matchers}
import org.scalatest.mock.MockitoSugar
import com.twitter.conversions.time._

class ConfigSpec extends WordSpec with Matchers with MockitoSugar {
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
      foo.didIt shouldBe false
      (foo.y: Int) shouldEqual 25 // use type annotation to force implicit conversion
      foo.didIt shouldBe true
    }

    "subclass can override indepedent var for use in dependent var" in {
      class Foo extends Config.Nothing {
        var x = 10
        var y = computed(x * 2 + 5)
      }
      val bar = new Foo {
        x = 20
      }
      (bar.y: Int) shouldEqual 45 // use type annotation to force implicit conversion
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
        foo.missingValues shouldBe empty
      }

      "must find top-level missing values" in {
        val foo = new Foo
        foo.missingValues should contain theSameElementsAs(Seq("x", "bar"))
      }

      "must find top-level and nested missing values" in {
        val foo = new Foo {
          bar = new Bar
        }
        foo.missingValues should contain theSameElementsAs(Seq("x", "bar.z"))
      }

      "must find nested missing values in optional sub-configs" in {
        val foo = new Foo {
          x = 3
          bar = new Bar {
            z = 1
          }
          baz = new Baz
        }
        foo.missingValues should contain theSameElementsAs(Seq("baz.w"))
      }
    }
  }
}
