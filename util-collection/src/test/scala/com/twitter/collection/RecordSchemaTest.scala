package com.twitter.collection

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RecordSchemaTest extends FunSuite {

  val schema                       = new RecordSchema
  val field                        = schema.newMutableField[Int]()
  val fieldWithDefault             = schema.newMutableField[Int](42)
  val nullableField                = schema.newMutableField[String]()
  val nullableFieldWithNullDefault = schema.newMutableField[String](null)

  // filthy: the default value is allocated freshly each time it's accessed
  val fieldWithFilthyDefault       = schema.newField[Object](new Object)

  test("get should return None when field with no default is missing") {
    val record = schema.newRecord()
    assert(record.get(field) == None)
    assert(record.get(nullableField) == None)
  }

  test("get should return Some(default) when field with default is missing") {
    val record = schema.newRecord()
    assert(record.get(fieldWithDefault) == Some(42))
    assert(record.get(nullableFieldWithNullDefault) == Some(null))
  }

  test("get should return Some(field value) when field with no default is present") {
    val record = schema.newRecord()
      .put(field, 31337)
      .put(nullableField, null)
    assert(record.get(field) == Some(31337))
    assert(record.get(nullableField) == Some(null))
  }

  test("get should return Some(field value) when field with default is present") {
    val record = schema.newRecord()
      .put(fieldWithDefault, 31337)
      .put(nullableFieldWithNullDefault, null)
    assert(record.get(fieldWithDefault) == Some(31337))
    assert(record.get(nullableFieldWithNullDefault) == Some(null))
  }

  test("apply should throw NoSuchElementException when field with no default is missing") {
    val record = schema.newRecord()
    intercept[java.util.NoSuchElementException] {
      record(field)
    }
    intercept[java.util.NoSuchElementException] {
      record(nullableField)
    }
  }

  test("apply should return default when field with default is missing") {
    val record = schema.newRecord()
    assert(record(fieldWithDefault) == 42)
    assert(record(nullableFieldWithNullDefault) == null)
  }

  test("apply should return field value when field with no default is present") {
    val record = schema.newRecord()
      .put(field, 31337)
      .put(nullableField, null)
    assert(record(field) == 31337)
    assert(record(nullableField) == null)
  }

  test("apply should return field value when field with default is present") {
    val record = schema.newRecord()
      .put(fieldWithDefault, 31337)
      .put(nullableFieldWithNullDefault, null)
    assert(record(fieldWithDefault) == 31337)
    assert(record(nullableFieldWithNullDefault) == null)
  }

  test("put should insert when field is not already present") {
    // case covered above, in "when field is present" tests
  }

  test("put should throw IllegalStateException when field is already present") {
    val record = schema.newRecord()
      .put(field, 31337)
      .put(nullableField, null)
    intercept[java.lang.IllegalStateException] {
      record.put(field, 31338)
    }
    intercept[java.lang.IllegalStateException] {
      record.put(nullableField, "not null")
    }
  }

  test("update should insert when field is not already present") {
    val record = schema.newRecord()
      .update(field, 31337)
      .update(nullableField, null)
    assert(record(field) == 31337)
    assert(record(nullableField) == null)
  }

  test("update should overwrite when field is already present") {
    val record = schema.newRecord()
      .update(field, 31337)
      .update(field, 31338)
      .update(nullableField, null)
      .update(nullableField, "not null")
    assert(record(field) == 31338)
    assert(record(nullableField) == "not null")
  }

  test("default values should be evaluated freshly for each record instance") {
    val record1 = schema.newRecord()
    val record2 = schema.newRecord()
    assert(record1(fieldWithFilthyDefault) != record2(fieldWithFilthyDefault))
  }

  test("default values should be evaluated only once for each record instance") {
    val record = schema.newRecord()
    assert(record(fieldWithFilthyDefault) == record(fieldWithFilthyDefault))
  }

}
