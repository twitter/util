package com.twitter.util.logging

import com.twitter.io.TempFolder
import java.io._
import org.scalatest.funsuite.AnyFunSuite

class SerializableLoggingTest extends AnyFunSuite with TempFolder {

  def read[T](filename: String): T = {
    new ObjectInputStream(new FileInputStream(new File(folderName, filename)))
      .readObject()
      .asInstanceOf[T]
  }

  def write(filename: String, obj: Object): Unit = {
    new ObjectOutputStream(new FileOutputStream(new File(folderName, filename))).writeObject(obj)
  }

  test("Logging#serialized object with Logging") {

    withTempFolder {
      val toSerialize = new TestSerializable(2, 7)
      val toSerializeTotal = toSerialize.total
      write("temp.txt", toSerialize)

      val toRead = read[TestSerializable]("temp.txt")
      val toReadTotal = toRead.total
      assert(toSerialize == toRead)
      assert(toSerializeTotal == toReadTotal)
    }
  }

  test("Logging#usable after serialization with underlying") {
    withTempFolder {
      write("test.txt", Logger(org.slf4j.LoggerFactory.getLogger("test")))
      val logger = read[Logger]("test.txt")

      logger.info("After deserialization")
    }
  }

  test("Logging#usable after serialization with classOf") {
    withTempFolder {
      write("classOf.txt", Logger(classOf[SerializableLoggingTest]))
      val logger = read[Logger]("classOf.txt")

      logger.info("After deserialization")
    }
  }

  test("Logging#usable after serialization with class") {
    withTempFolder {
      write("class.txt", Logger[SerializableLoggingTest])
      val logger = read[Logger]("class.txt")

      logger.info("After deserialization")
    }
  }

}
