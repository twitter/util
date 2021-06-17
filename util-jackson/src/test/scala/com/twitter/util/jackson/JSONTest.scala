package com.twitter.util.jackson

import com.twitter.io.Buf
import java.io.{ByteArrayInputStream, File => JFile}
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class JSONTest extends AnyFunSuite with Matchers with FileResources {

  test("JSON#parse 1") {
    JSON.parse[Map[String, Int]]("""{"a": 1, "b": 2}""") match {
      case Some(map) =>
        map should be(Map("a" -> 1, "b" -> 2))
      case _ => fail()
    }
  }

  test("JSON#parse 2") {
    JSON.parse[Seq[String]]("""["a", "b", "c"]""") match {
      case Some(Seq("a", "b", "c")) => // pass
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

  test("JSON#parse 5") {
    val buf = Buf.Utf8("""{"id": "abcd1234"}""")
    JSON.parse[FooClass](buf) match {
      case Some(foo) =>
        foo.id should equal("abcd1234")
      case _ => fail()
    }
  }

  test("JSON#parse 6") {
    val inputStream = new ByteArrayInputStream("""{"id": "abcd1234"}""".getBytes("UTF-8"))
    try {
      JSON.parse[FooClass](inputStream) match {
        case Some(foo) =>
          foo.id should equal("abcd1234")
        case _ => fail()
      }
    } finally {
      inputStream.close()
    }
  }

  test("JSON#parse 7") {
    withTempFolder {
      val file: JFile =
        writeStringToFile(folderName, "test-file", ".json", """{"id": "999999999"}""")
      JSON.parse[FooClass](file) match {
        case Some(foo) =>
          foo.id should equal("999999999")
        case _ => fail()
      }
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

  test("JSON.Resource#parse resource") {
    JSON.Resource.parse[FooClass]("/test.json") match {
      case Some(foo) =>
        foo.id should equal("55555555")
      case _ => fail()
    }
  }
}
