package com.twitter.util.validation.internal.validators

import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PackageObjectTest extends AnyFunSuite with Matchers {

  test("mkString with more than 1 entry - Seq") {
    mkString(Seq(1, 2)) should equal("[1, 2]")
  }

  test("mkString with map entries") {
    val m: Map[String, String] = Map("foo" -> "1", "bar" -> "2", "baz" -> "3")
    mkString(m) should equal("[(foo,1), (bar,2), (baz,3)]")
  }

  test("mkString with option") {
    val o = Some("foo")
    mkString(o) should equal("foo")
  }

  test("mkString with more than 1 entry - Seq some empty values") {
    mkString(Seq(" ", " 33")) should equal("33")
    mkString(Seq(" ", " 33", " ")) should equal("33")
    mkString(Seq(" ", " ", " ", " 33", " ")) should equal("33")
  }

  test("mkString with more than 1 entry - Array") {
    mkString(Array(1, 2)) should equal("[1, 2]")
  }

  test("mkString with 1 entry - Seq") {
    mkString(Seq(1)) should equal("1")
    mkString(Seq(-1)) should equal("-1")
    mkString(Seq("!!")) should equal("!!")
  }

  test("mkString with 1 entry - Array") {
    mkString(Array(1)) should equal("1")
  }

  test("mkString with no entries - Seq") {
    mkString(Seq(" ", " ")) should equal("<empty>")
    mkString(Seq.empty[Int]) should equal("<empty>")
    mkString(Seq.empty[String]) should equal("<empty>")
  }

  test("mkString with no entries - Array") {
    mkString(Array(" ", " ")) should equal("<empty>")
    mkString(Array.empty[Int]) should equal("<empty>")
    mkString(Array.empty[String]) should equal("<empty>")
  }

  test("mkString with no entries - Map") {
    mkString(Map("" -> "")) should equal("(,)")
    mkString(Map.empty[String, Int]) should equal("<empty>")
    mkString(Map.empty) should equal("<empty>")
  }

  test("mkString with no entries - Option") {
    mkString(None) should equal("<empty>")
    mkString(Option(null)) should equal("<empty>")
    mkString(Option("")) should equal("<empty>")
    mkString(Some("")) should equal("<empty>")
  }
}
