package com.twitter.util.jackson

import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class JSONTest extends AnyFunSuite with Matchers {

  test("JSON#parse 1") {
    JSON.parse[Map[String, Int]]("""{"a": 1, "b": 2}""") match {
      case Some(map) =>
        map("a") should equal(1)
        map("b") should equal(2)
      case _ => fail()
    }
  }

  test("JSON#parse 2") {
    JSON.parse[Seq[String]]("""["a", "b", "c"]""") match {
      case Some(seq) =>
        seq.size should equal(3)
        seq.head should be("a")
        seq(1) should be("b")
        seq.last should be("c")
      case _ => fail()
    }
  }

  test("JSON#parse 3") {
    JSON.parse[FooClass]("""{"id": "abcd1234"}""") match {
      case Some(foo) =>
        foo.id should equal("abcd1234")
      case _ => fail()
    }
  }

  test("JSON#parse 4") {
    JSON.parse[CaseClassWithNotEmptyValidation]("""{"name": "", "make": "vw"}""") match {
      case Some(clazz) =>
        // performs no validations
        clazz.name.isEmpty should be(true)
        clazz.make should equal(CarMakeEnum.vw)
      case _ => fail()
    }
  }

  test("JSON#write 1") {
    JSON.write(Map("a" -> 1, "b" -> 2)) should equal("""{"a":1,"b":2}""")
  }

  test("JSON#write 2") {
    JSON.write(Seq("a", "b", "c")) should equal("""["a","b","c"]""")
  }

  test("JSON#write 3") {
    JSON.write(FooClass("abcd1234")) should equal("""{"id":"abcd1234"}""")
  }

  test("JSON#prettyPrint 1") {
    JSON.prettyPrint(Map("a" -> 1, "b" -> 2)) should equal("""{
                                                             |  "a" : 1,
                                                             |  "b" : 2
                                                             |}""".stripMargin)
  }

  test("JSON#prettyPrint 2") {
    JSON.prettyPrint(Seq("a", "b", "c")) should equal("""[
                                                        |  "a",
                                                        |  "b",
                                                        |  "c"
                                                        |]""".stripMargin)
  }

  test("JSON#prettyPrint 3") {
    JSON.prettyPrint(FooClass("abcd1234")) should equal("""{
        |  "id" : "abcd1234"
        |}""".stripMargin)
  }
}
