package com.twitter.util

import org.specs.Specification
import org.specs.mock.Mockito
import com.twitter.conversions.time._

class ConfigSpec extends Specification with Mockito {
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
      foo.didIt must beFalse
      (foo.y: Int) mustEqual 25 // use type annotation to force implicit conversion
      foo.didIt must beTrue
    }

    "subclass can override indepedent var for use in dependent var" in {
      class Foo extends Config.Nothing {
        var x = 10
        var y = computed(x * 2 + 5)
      }
      val bar = new Foo {
        x = 20
      }
      (bar.y: Int) mustEqual 45 // use type annotation to force implicit conversion
    }

    "missingValues" in {
      class Bar extends Config.Nothing {
        var z = required[Int]
      }
      class Foo extends Config.Nothing {
        var x = required[Int]
        var y = 3
        var bar = required[Bar]
      }

      "must return empty Seq when no values are missing" in {
        val foo = new Foo {
          x = 42
          bar = new Bar {
            z = 10
          }
        }
        foo.missingValues must beEmpty
      }

      "must find top-level missing values" in {
        val foo = new Foo
        foo.missingValues must haveSameElementsAs(Seq("x", "bar"))
      }

      "must find top-level and nested missing values" in {
        val foo = new Foo {
          bar = new Bar
        }
        foo.missingValues must haveSameElementsAs(Seq("x", "bar.z"))
      }
    }
  }
}
