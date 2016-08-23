package com.twitter.logging

import java.util.logging.{LogRecord => JRecord}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LogRecordTest extends FunSuite {
  test("LogRecord should getMethod properly") {
    Logger.withLoggers(Nil) {
      new LogRecordTestHelper({ r: JRecord => r.getSourceMethodName() }) {
        def makingLogRecord() = {
          logger.log(Level.INFO, "OK")
          assert(handler.get == "makingLogRecord")
        }
        makingLogRecord()
      }
    }
  }

  test("LogRecord should getClass properly") {
    Logger.withLoggers(Nil) {
      new Foo {
        assert(handler.get == "com.twitter.logging.Foo")
      }
    }
  }
}

abstract class LogRecordTestHelper(formats: JRecord => String) {
  val formatter = new Formatter {
    override def format(r: JRecord): String = formats(r)
  }
  val handler = new StringHandler(formatter)
  val logger = Logger.get("")
  logger.addHandler(handler)
}

class Foo extends LogRecordTestHelper({ r: JRecord => r.getSourceClassName() }) {
  def makingLogRecord() {
    logger.log(Level.INFO, "OK")
  }

  makingLogRecord()
}
