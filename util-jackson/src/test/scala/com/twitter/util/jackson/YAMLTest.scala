package com.twitter.util.jackson

import com.twitter.io.Buf
import java.io.{ByteArrayInputStream, File => JFile}
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class YAMLTest extends AnyFunSuite with Matchers with FileResources {

  test("YAML#parse 1") {
    YAML.parse[Map[String, Int]]("""
        |---
        |a: 1
        |b: 2
        |c: 3""".stripMargin) match {
      case Some(map) =>
        map should be(Map("a" -> 1, "b" -> 2, "c" -> 3))
      case _ => fail()
    }
  }

  test("YAML#parse 2") {
    // without quotes
    YAML.parse[Seq[String]]("""
        |---
        |- a
        |- b
        |- c""".stripMargin) match {
      case Some(Seq("a", "b", "c")) => // pass
      case _ => fail()
    }
    // with quotes
    YAML.parse[Seq[String]]("""
                              |---
                              |- "a"
                              |- "b"
                              |- "c"""".stripMargin) match {
      case Some(Seq("a", "b", "c")) => // pass
      case _ => fail()
    }
  }

  test("YAML#parse 3") {
    // without quotes
    YAML.parse[FooClass]("""
                           |---
                           |id: abcde1234""".stripMargin) match {
      case Some(foo) =>
        foo.id should equal("abcde1234")
      case _ => fail()
    }
    // with quotes
    YAML.parse[FooClass]("""
                           |---
                           |id: "abcde1234"""".stripMargin) match {
      case Some(foo) =>
        foo.id should equal("abcde1234")
      case _ => fail()
    }
  }

  test("YAML#parse 4") {
    YAML.parse[CaseClassWithNotEmptyValidation]("""
                                                  |---
                                                  |name: ''
                                                  |make: vw""".stripMargin) match {
      case Some(clazz) =>
        // performs no validations
        clazz.name.isEmpty should be(true)
        clazz.make should equal(CarMakeEnum.vw)
      case _ => fail()
    }
  }

  test("YAML#parse 5") {
    val buf = Buf.Utf8("""{"id": "abcd1234"}""")
    YAML.parse[FooClass](buf) match {
      case Some(foo) =>
        foo.id should equal("abcd1234")
      case _ => fail()
    }
  }

  test("YAML#parse 6") {
    val inputStream = new ByteArrayInputStream("""{"id": "abcd1234"}""".getBytes("UTF-8"))
    try {
      YAML.parse[FooClass](inputStream) match {
        case Some(foo) =>
          foo.id should equal("abcd1234")
        case _ => fail()
      }
    } finally {
      inputStream.close()
    }
  }

  test("YAML#parse 7") {
    withTempFolder {
      // without quotes
      val file1: JFile =
        writeStringToFile(
          folderName,
          "test-file-quotes",
          ".yaml",
          """|---
             |id: 999999999""".stripMargin)
      YAML.parse[FooClass](file1) match {
        case Some(foo) =>
          foo.id should equal("999999999")
        case _ => fail()
      }
      // with quotes
      val file2: JFile =
        writeStringToFile(
          folderName,
          "test-file-noquotes",
          ".yaml",
          """|---
             |id: "999999999"""".stripMargin)
      YAML.parse[FooClass](file2) match {
        case Some(foo) =>
          foo.id should equal("999999999")
        case _ => fail()
      }
    }
  }

  test("YAML#write 1") {
    YAML.write(Map("a" -> 1, "b" -> 2)) should equal("""|---
        |a: 1
        |b: 2
        |""".stripMargin)
  }

  test("YAML#write 2") {
    YAML.write(Seq("a", "b", "c")) should equal("""|---
        |- "a"
        |- "b"
        |- "c"
        |""".stripMargin)
  }

  test("YAML#write 3") {
    YAML.write(FooClass("abcd1234")) should equal("""|---
        |id: "abcd1234"
        |""".stripMargin)
  }

  test("YAML.Resource#parse resource") {
    YAML.Resource.parse[FooClass]("/test.yml") match {
      case Some(foo) =>
        foo.id should equal("55555555")
      case _ => fail()
    }
  }
}
