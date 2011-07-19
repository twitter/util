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
import org.specs.Specification
import config._

class LoggerSpec extends Specification with TempFolder with TestLogging {
  private var myHandler: Handler = null
  private var log: Logger = null

  val timeFrozenFormatter = new FormatterConfig { timezone = "UTC" }.apply()
  val timeFrozenHandler = new StringHandler(timeFrozenFormatter, None) {
    override def publish(record: javalog.LogRecord) = {
      record.setMillis(1206769996722L)
      super.publish(record)
    }
  }

  private def parse(): List[String] = {
    val rv = myHandler.asInstanceOf[StringHandler].get.split("\n")
    myHandler.asInstanceOf[StringHandler].clear()
    rv.toList
  }

  "Logger" should {
    doBefore {
      Logger.clearHandlers
      timeFrozenHandler.clear()
      myHandler = new StringHandler(BareFormatter, None)
      log = Logger.get("")
      log.setLevel(Level.ERROR)
      log.addHandler(myHandler)
    }

    "provide level name and value maps" in {
      Logger.levels mustEqual Map(
        Level.ALL.value -> Level.ALL,
        Level.TRACE.value -> Level.TRACE,
        Level.DEBUG.value -> Level.DEBUG,
        Level.INFO.value -> Level.INFO,
        Level.WARNING.value -> Level.WARNING,
        Level.ERROR.value -> Level.ERROR,
        Level.CRITICAL.value -> Level.CRITICAL,
        Level.FATAL.value -> Level.FATAL,
        Level.OFF.value -> Level.OFF)
      Logger.levelNames mustEqual Map(
        "ALL" -> Level.ALL,
        "TRACE" -> Level.TRACE,
        "DEBUG" -> Level.DEBUG,
        "INFO" -> Level.INFO,
        "WARNING" -> Level.WARNING,
        "ERROR" -> Level.ERROR,
        "CRITICAL" -> Level.CRITICAL,
        "FATAL" -> Level.FATAL,
        "OFF" -> Level.OFF)
    }

    "figure out package names" in {
      val log1 = Logger(getClass)
      log1.name mustEqual "com.twitter.logging.LoggerSpec"
    }

    "log & trace a message" in {
      traceLogger(Level.INFO)
      Logger.get("").info("angry duck")
      mustLog("duck")
    }

    "log a message, with timestamp" in {
      Logger.clearHandlers()
      myHandler = timeFrozenHandler
      log.addHandler(timeFrozenHandler)
      log.error("error!")
      parse() mustEqual List("ERR [20080329-05:53:16.722] (root): error!")
    }

    "get single-threaded return the same value" in {
      val loggerFirst = Logger.get("getTest")
      loggerFirst must notBeNull

      val loggerSecond = Logger.get("getTest")
      loggerSecond must be(loggerFirst)
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
      executorService.awaitTermination(10, TimeUnit.SECONDS) must beTrue

      // now make sure they are all the same reference
      val expected = futureResults(0).get
      for (i <- 1.until(numThreads)) {
        val result = futureResults(i).get
        result must be(expected)
      }
    }

    "configure logging" in {
      doBefore {
        Logger.clearHandlers()
      }

      "file handler" in {
        withTempFolder {
          val log = new LoggerConfig {
            node = "com.twitter"
            level = Level.DEBUG
            handlers = new FileHandlerConfig {
              filename = folderName + "/test.log"
              roll = Policy.Never
              append = false
              level = Level.INFO
              formatter = new FormatterConfig {
                useFullPackageNames = true
                truncateAt = 1024
                prefix = "%s <HH:mm> %s"
              }
            } :: Nil
          }.apply()

          log.getLevel mustEqual Level.DEBUG
          log.getHandlers().length mustEqual 1
          val handler = log.getHandlers()(0).asInstanceOf[FileHandler]
          handler.filename mustEqual folderName + "/test.log"
          handler.append mustEqual false
          handler.getLevel mustEqual Level.INFO
          val formatter = handler.formatter
          formatter.formatPrefix(javalog.Level.WARNING, "10:55", "hello") mustEqual "WARNING 10:55 hello"
          log.name mustEqual "com.twitter"
          formatter.truncateAt mustEqual 1024
          formatter.useFullPackageNames mustEqual true
        }
      }

      "syslog handler" in {
        withTempFolder {
          val log = new LoggerConfig {
            node = "com.twitter"
            handlers = new SyslogHandlerConfig {
              formatter = new SyslogFormatterConfig {
                serverName = "elmo"
                priority = 128
              }
              server = "example.com"
              port = 212
            } :: Nil
          }.apply()

          log.getHandlers.length mustEqual 1
          val h = log.getHandlers()(0).asInstanceOf[SyslogHandler]
          h.dest.asInstanceOf[InetSocketAddress].getHostName mustEqual "example.com"
          h.dest.asInstanceOf[InetSocketAddress].getPort mustEqual 212
          val formatter = h.formatter.asInstanceOf[SyslogFormatter]
          formatter.serverName mustEqual Some("elmo")
          formatter.priority mustEqual 128
        }
      }

      "complex config" in {
        withTempFolder {
          val config = new LoggerConfig {
            level = Level.INFO
            handlers = new ThrottledHandlerConfig {
              duration = 60.seconds
              maxToDisplay = 10
              handler = new FileHandlerConfig {
                filename = folderName + "/production.log"
                roll = Policy.SigHup
                formatter = new FormatterConfig {
                  truncateStackTracesAt = 100
                }
              }
            }
          } :: new LoggerConfig {
            node = "w3c"
            level = Level.OFF
            useParents = false
          } :: new LoggerConfig {
            node = "stats"
            level = Level.INFO
            useParents = false
            handlers = new ScribeHandlerConfig {
              formatter = BareFormatterConfig
              maxMessagesToBuffer = 100
              category = "cuckoo_json"
            }
          } :: new LoggerConfig {
            node = "bad_jobs"
            level = Level.INFO
            useParents = false
            handlers = new FileHandlerConfig {
              filename = folderName + "/bad_jobs.log"
              roll = Policy.Never
            }
          } :: Nil

          Logger.configure(config)
          Logger.get("").getLevel mustEqual Level.INFO
          Logger.get("w3c").getLevel mustEqual Level.OFF
          Logger.get("stats").getLevel mustEqual Level.INFO
          Logger.get("bad_jobs").getLevel mustEqual Level.INFO
          Logger.get("").getHandlers()(0) must haveClass[ThrottledHandler]
          Logger.get("").getHandlers()(0).asInstanceOf[ThrottledHandler].handler must haveClass[FileHandler]
          Logger.get("w3c").getHandlers().size mustEqual 0
          Logger.get("stats").getHandlers()(0) must haveClass[ScribeHandler]
          Logger.get("bad_jobs").getHandlers()(0) must haveClass[FileHandler]
        }
      }
    }
  }
}
