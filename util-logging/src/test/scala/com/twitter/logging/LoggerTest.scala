/*
 * Copyright 2010 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.logging

import java.net.InetSocketAddress
import java.util.concurrent.{Callable, CountDownLatch, Executors, Future, TimeUnit}
import java.util.{logging => javalog}
import scala.collection.mutable
import com.twitter.conversions.string._
import com.twitter.conversions.time._
import com.twitter.util.TempFolder
import org.scalatest.WordSpec

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import org.mockito.Mockito._

@RunWith(classOf[JUnitRunner])
class LoggerTest extends WordSpec with TempFolder with TestLogging {
  class LoggerSpecHelper {
    var myHandler: Handler = null
    var log: Logger = null

    val timeFrozenFormatter = new Formatter(timezone = Some("UTC"))
    val timeFrozenHandler = new StringHandler(timeFrozenFormatter, None) {
      override def publish(record: javalog.LogRecord) = {
        record.setMillis(1206769996722L)
        super.publish(record)
      }
    }

    def parse(): List[String] = {
      val rv = myHandler.asInstanceOf[StringHandler].get.split("\n")
      myHandler.asInstanceOf[StringHandler].clear()
      rv.toList
    }
  }

  "Logger" should {
    val h = new LoggerSpecHelper
    import h._

    def before() = {
      Logger.clearHandlers()
      timeFrozenHandler.clear()
      myHandler = new StringHandler(BareFormatter, None)
      log = Logger.get("")
      log.setLevel(Level.ERROR)
      log.addHandler(myHandler)
    }

    "provide level name and value maps" in {
      assert(Logger.levels === Map(
        Level.ALL.value -> Level.ALL,
        Level.TRACE.value -> Level.TRACE,
        Level.DEBUG.value -> Level.DEBUG,
        Level.INFO.value -> Level.INFO,
        Level.WARNING.value -> Level.WARNING,
        Level.ERROR.value -> Level.ERROR,
        Level.CRITICAL.value -> Level.CRITICAL,
        Level.FATAL.value -> Level.FATAL,
        Level.OFF.value -> Level.OFF))
      assert(Logger.levelNames === Map(
        "ALL" -> Level.ALL,
        "TRACE" -> Level.TRACE,
        "DEBUG" -> Level.DEBUG,
        "INFO" -> Level.INFO,
        "WARNING" -> Level.WARNING,
        "ERROR" -> Level.ERROR,
        "CRITICAL" -> Level.CRITICAL,
        "FATAL" -> Level.FATAL,
        "OFF" -> Level.OFF))
    }

    "figure out package names" in {
      val log1 = Logger(this.getClass)
      assert(log1.name === "com.twitter.logging.LoggerTest")
    }

    "log & trace a message" in {
      traceLogger(Level.INFO)
      Logger.get("").info("angry duck")
      mustLog("duck")
    }

    "log a message, with timestamp" in {
      before()

      Logger.clearHandlers()
      myHandler = timeFrozenHandler
      log.addHandler(timeFrozenHandler)
      log.error("error!")
      assert(parse() === List("ERR [20080329-05:53:16.722] (root): error!"))
    }

    "get single-threaded return the same value" in {
      val loggerFirst = Logger.get("getTest")
      assert(loggerFirst !== null)

      val loggerSecond = Logger.get("getTest")
      assert(loggerSecond === loggerFirst)
    }

    "get multi-threaded return the same value" in {
      val numThreads = 10
      val latch = new CountDownLatch(1)

      // queue up the workers
      val executorService = Executors.newFixedThreadPool(numThreads)
      val futureResults = new mutable.ListBuffer[Future[Logger]]
      for (i <- 0.until(numThreads)) {
        val future = executorService.submit(new Callable[Logger]() {
          def call(): Logger = {
            latch.await(10, TimeUnit.SECONDS)
            return Logger.get("concurrencyTest")
          }
        })
        futureResults += future
      }
      executorService.shutdown
      // let them rip, and then wait for em to finish
      latch.countDown
      assert(executorService.awaitTermination(10, TimeUnit.SECONDS) === true)

      // now make sure they are all the same reference
      val expected = futureResults(0).get
      for (i <- 1.until(numThreads)) {
        val result = futureResults(i).get
        assert(result === expected)
      }
    }

    "withLoggers applies logger factories, executes a block, and then applies original factories" in {
      val initialFactories = List(LoggerFactory(node = "", level = Some(Level.DEBUG)))
      val otherFactories = List(LoggerFactory(node = "", level = Some(Level.INFO)))
      Logger.configure(initialFactories)

      assert(Logger.get("").getLevel === Level.DEBUG)
      Logger.withLoggers(otherFactories) {
        assert(Logger.get("").getLevel() === Level.INFO)
      }
      assert(Logger.get("").getLevel === Level.DEBUG)
    }

    "configure logging" should {
      def before {
        Logger.clearHandlers()
      }

      "file handler" in {
        withTempFolder {
          val log: Logger = LoggerFactory(
            node = "com.twitter",
            level = Some(Level.DEBUG),
            handlers = FileHandler(
              filename = folderName + "/test.log",
              rollPolicy = Policy.Never,
              append = false,
              level = Some(Level.INFO),
              formatter = new Formatter(
                useFullPackageNames = true,
                truncateAt = 1024,
                prefix = "%s <HH:mm> %s"
              )
            ) :: Nil
          ).apply()

          assert(log.getLevel === Level.DEBUG)
          assert(log.getHandlers().length === 1)
          val handler = log.getHandlers()(0).asInstanceOf[FileHandler]
          assert(handler.filename === folderName + "/test.log")
          assert(handler.append === false)
          assert(handler.getLevel === Level.INFO)
          val formatter = handler.formatter
          assert(formatter.formatPrefix(javalog.Level.WARNING, "10:55", "hello") === "WARNING 10:55 hello")
          assert(log.name === "com.twitter")
          assert(formatter.truncateAt === 1024)
          assert(formatter.useFullPackageNames === true)
        }
      }

      "syslog handler" in {
        withTempFolder {
          val log: Logger = LoggerFactory(
            node = "com.twitter",
            handlers = SyslogHandler(
              formatter = new SyslogFormatter(
                serverName = Some("elmo"),
                priority = 128
              ),
              server = "example.com",
              port = 212
            ) :: Nil
          ).apply()

          assert(log.getHandlers.length === 1)
          val h = log.getHandlers()(0).asInstanceOf[SyslogHandler]
          assert(h.dest.asInstanceOf[InetSocketAddress].getHostName === "example.com")
          assert(h.dest.asInstanceOf[InetSocketAddress].getPort === 212)
          val formatter = h.formatter.asInstanceOf[SyslogFormatter]
          assert(formatter.serverName === Some("elmo"))
          assert(formatter.priority === 128)
        }
      }

      "complex config" in {
        withTempFolder {
          val factories = LoggerFactory(
            level = Some(Level.INFO),
            handlers = ThrottledHandler(
              duration = 60.seconds,
              maxToDisplay = 10,
              handler = FileHandler(
                filename = folderName + "/production.log",
                rollPolicy = Policy.SigHup,
                formatter = new Formatter(
                  truncateStackTracesAt = 100
                )
              )
            ) :: Nil
          ) :: LoggerFactory(
            node = "w3c",
            level = Some(Level.OFF),
            useParents = false
          ) :: LoggerFactory(
            node = "stats",
            level = Some(Level.INFO),
            useParents = false,
            handlers = ScribeHandler(
              formatter = BareFormatter,
              maxMessagesToBuffer = 100,
              category = "cuckoo_json"
            ) :: Nil
          ) :: LoggerFactory(
            node = "bad_jobs",
            level = Some(Level.INFO),
            useParents = false,
            handlers = FileHandler(
              filename = folderName + "/bad_jobs.log",
              rollPolicy = Policy.Never
            ) :: Nil
          ) :: Nil

          Logger.configure(factories)
          assert(Logger.get("").getLevel === Level.INFO)
          assert(Logger.get("w3c").getLevel === Level.OFF)
          assert(Logger.get("stats").getLevel === Level.INFO)
          assert(Logger.get("bad_jobs").getLevel === Level.INFO)
          try {
          Logger.get("").getHandlers()(0).asInstanceOf[ThrottledHandler]
          } catch {
            case _: ClassCastException => fail("not a ThrottledHandler")
          }
          try {
            Logger.get("").getHandlers()(0).asInstanceOf[ThrottledHandler].handler.asInstanceOf[FileHandler]
          } catch {
            case _: ClassCastException => fail("not a FileHandler")
          }
          assert(Logger.get("w3c").getHandlers().size === 0)
          try {
            Logger.get("stats").getHandlers()(0).asInstanceOf[ScribeHandler]
          } catch {
            case _: ClassCastException => fail("not a ScribeHandler")
          }
          try {
            Logger.get("bad_jobs").getHandlers()(0).asInstanceOf[FileHandler]
          } catch {
            case _: ClassCastException => fail("not a FileHandler")
          }

        }
      }
    }

    "java logging" should {
      val logger = javalog.Logger.getLogger("")

      def before() = {
        traceLogger(Level.INFO)
      }

      "single arg calls" in {
        before()
        logger.log(javalog.Level.INFO, "V1={0}", "A")
        mustLog("V1=A")
      }

      "varargs calls" in {
        before()
        logger.log(javalog.Level.INFO, "V1={0}, V2={1}", Array[AnyRef]("A", "B"))
        mustLog("V1=A, V2=B")
      }

      "invalid message format" in {
        before()
        logger.log(javalog.Level.INFO, "V1=%s", "A")
        mustLog("V1=%s") // %s notation is not known in java MessageFormat
      }

      // logging in scala uses the %s format and not the Java MessageFormat
      "compare scala logging format" in {
        before()
        Logger.get("").info("V1{0}=%s","A")
        mustLog("V1{0}=A")
      }
    }
  }
}
