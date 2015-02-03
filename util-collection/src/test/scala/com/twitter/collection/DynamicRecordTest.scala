package com.twitter.collection

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class DynamicRecordTest extends FunSuite {

  val decl             = new DynamicRecord
  val field            = decl.newMutableField[Int]
  val fieldWithDefault = decl.newMutableField[Int](42)

  test("get should return None when field with no default is missing") {
    val record = decl.newInstance
    assert(record.get(field) == None)
  }

  test("get should return Some(default) when field with default is missing") {
    val record = decl.newInstance
    assert(record.get(fieldWithDefault) == Some(42))
  }

  test("get should return Some(field value) when field with no default is present") {
    val record = decl.newInstance
      .put(field, 31337)
    assert(record.get(field) == Some(31337))
  }

  test("get should return Some(field value) when field with default is present") {
    val record = decl.newInstance
      .put(fieldWithDefault, 31337)
    assert(record.get(fieldWithDefault) == Some(31337))
  }

  test("apply should throw NoSuchElementException when field with no default is missing") {
    val record = decl.newInstance
    intercept[java.util.NoSuchElementException] {
      record(field)
    }
  }

  test("apply should return default when field with default is missing") {
    val record = decl.newInstance
    assert(record(fieldWithDefault) == 42)
  }

  test("apply should return field value when field with no default is present") {
    val record = decl.newInstance
      .put(field, 31337)
    assert(record(field) == 31337)
  }

  test("apply should return field value when field with default is present") {
    val record = decl.newInstance
      .put(fieldWithDefault, 31337)
    assert(record(fieldWithDefault) == 31337)
  }

  test("put should insert when field is not already present") {
    // case covered above, in "when field is present" tests
  }

  test("put should throw IllegalStateException when field is already present") {
    val record = decl.newInstance
      .put(field, 31337)
    intercept[java.lang.IllegalStateException] {
      record.put(field, 31338)
    }
  }

  test("update should insert when field is not already present") {
    val record = decl.newInstance
      .update(field, 31337)
    assert(record(field) == 31337)
  }

  test("update should overwrite when field is already present") {
    val record = decl.newInstance
      .update(field, 31337)
      .update(field, 31338)
    assert(record(field) == 31338)
  }
}
