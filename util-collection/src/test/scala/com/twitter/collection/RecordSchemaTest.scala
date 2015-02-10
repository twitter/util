package com.twitter.collection

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RecordSchemaTest extends FunSuite {

  val schema           = new RecordSchema
  val field            = schema.newField[Object]()
  val fieldWithDefault = schema.newField[Object](new Object)
  val fields           = Seq(field, fieldWithDefault)

  test("apply should throw IllegalStateException when field is uninitialized") {
    val record = schema.newRecord()
    intercept[IllegalStateException] {
      record(field)
    }
  }

  test("apply should compute, store and return default when field is initialized with default") {
    val record = schema.newRecord()
    assert(record(fieldWithDefault) eq record(fieldWithDefault))
  }

  test("apply should return field value when field is explicitly initialized") {
    val record = schema.newRecord()
    val value = new Object

    for (f <- fields) {
      record(f) = value
      assert(record(f) eq value)
    }
  }

  test("lock should throw IllegalStateException when field is uninitialized") {
    val record = schema.newRecord()
    intercept[IllegalStateException] {
      record.lock(field)
    }
  }

  test("lock should compute and store default when field is initialized with default") {
    val record = schema.newRecord()
    record.lock(fieldWithDefault)
    assert(record(fieldWithDefault) ne null)
  }

  test("update should reassign when field is not locked") {
    val record = schema.newRecord()
    val value = new Object

    for (f <- fields) {
      record(f) = new Object
      record(f) = value
      assert(record(f) eq value)
    }
  }

  test("update should throw IllegalStateException when field is locked") {
    val record = schema.newRecord()
    val value = new Object

    for (f <- fields) {
      record(f) = value
      record.lock(f)
      intercept[IllegalStateException] {
        record(f) = value
      }
    }
  }

  test("updateAndLock should update and lock") {
    val record = schema.newRecord()
    val value = new Object

    for (f <- fields) {
      record.updateAndLock(f, value)
      intercept[IllegalStateException] {
        record(f) = value
      }
    }
  }

}
