/*
 * Copyright 2011 Twitter, Inc.
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

package com.twitter.util

import java.io.{File, IOException}
import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.parsing.combinator._
import scala.util.parsing.combinator.lexical._

/**
 * Simple helper to read authentication credentials from a text file.
 *
 * The file's format is assumed to be trivialized yaml, containing lines of the form ``key: value``.
 */
object AuthFile {
  object parser extends RegexParsers {
    override val whiteSpace = "(\\s+|#(.*?)\\n)+".r

    val token = "[\\w-_]+".r
    def auth = (token <~ ":") ~ "[^\\n]+".r ^^ { case k ~ v => (k, v) }
    def content: Parser[Map[String, String]] = rep(auth) ^^ { auths => Map(auths: _*) }

    def apply(in: String): Map[String, String] = {
      parseAll(content, in) match {
        case Success(result, _) => result
        case x @ Failure(msg, z) => throw new IOException(x.toString)
        case x @ Error(msg, _) => throw new IOException(x.toString)
      }
    }
  }

  def apply(file: File): Map[String, String] = parser(Source.fromFile(file).mkString)

  def apply(data: String): Map[String, String] = parser(data)
}

/**
 * Java interface to AuthFile.
 */
class AuthFile {
  def read(data: String): java.util.Map[String, String] = AuthFile(data).asJava
  def read(file: File): java.util.Map[String, String] = AuthFile(file).asJava
}
