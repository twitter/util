/*
 * Copyright 2012 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.logging

import java.util.{logging => javalog}

/**
 * Wrapper around `java.util.logging.LogRecord`. Should only be accessed from a single thread.
 *
 * Messages are formatted by Java's LogRecord using `java.text.MessageFormat` whereas
 * this class uses a regular `java.text.StringFormat`.
 *
 * This class takes [[com.twitter.logging.Logger]] into account when inferring the
 * `sourceMethod` and `sourceClass` names.
 */
class LogRecord(level: javalog.Level, msg: String) extends javalog.LogRecord(level, msg) {
  private[this] var inferred = false
  private[this] var sourceClassName: String = null
  private[this] var sourceMethodName: String = null

  // May be incorrect if called lazily
  override def getSourceClassName(): String = {
    if (!inferred)
      infer()
    sourceClassName
  }

  // May be incorrect if called lazily
  override def getSourceMethodName(): String = {
    if (!inferred)
      infer()
    sourceMethodName
  }

  override def setSourceClassName(name: String): Unit = {
    inferred = true
    sourceClassName = name
  }

  override def setSourceMethodName(name: String): Unit = {
    inferred = true
    sourceMethodName = name
  }

  private[this] def infer(): Unit = {
    // TODO: there is a small optimization we can do in jdk7 with new JavaLangAccess
    val stack = Thread.currentThread.getStackTrace()

    def notTwitterString(elt: StackTraceElement): Boolean =
      elt.getClassName != LogRecord.twitterString

    // Find the first non-Logger StackTraceElement after the first occurrence of Logger.
    val elt = stack dropWhile notTwitterString find notTwitterString

    val (cName, mName) = elt match {
      case Some(element) => (element.getClassName, element.getMethodName)
      case None => (super.getSourceClassName, super.getSourceMethodName)
    }
    setSourceMethodName(mName)
    setSourceClassName(cName)
  }
}

object LogRecord {
  private[logging] val twitterString = "com.twitter.logging.Logger"
}
