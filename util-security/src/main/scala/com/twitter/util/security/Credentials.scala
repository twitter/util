/*
 * Copyright 2011 Twitter, Inc.
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

package com.twitter.util.security

import java.io.File
import org.yaml.snakeyaml.Yaml
import scala.collection.JavaConverters._
import scala.io.Source

/**
 * Simple helper to read authentication credentials from a text file.
 *
 * The file's format is assumed to be yaml, containing string keys and values.
 */
object Credentials {
  private[this] val parser: ThreadLocal[Yaml] = new ThreadLocal[Yaml] {
    override def initialValue(): Yaml = new Yaml()
  }

  def byName(name: String): Map[String, String] = {
    apply(new File(sys.env.getOrElse("KEY_FOLDER", "/etc/keys"), name))
  }

  def apply(file: File): Map[String, String] = {
    val fileSource = Source.fromFile(file)
    try apply(fileSource.mkString)
    finally fileSource.close()
  }

  def apply(data: String): Map[String, String] = {
    val result: java.util.Map[String, Any] = parser.get.load(data)
    Option(result)
      .map(_.asScala.toMap.mapValues(v => if (v == null) "null" else v.toString)).getOrElse(
        Map.empty)
  }
}

/**
 * Java interface to Credentials.
 */
class Credentials {
  def read(data: String): java.util.Map[String, String] = Credentials(data).asJava
  def read(file: File): java.util.Map[String, String] = Credentials(file).asJava
  def byName(name: String): java.util.Map[String, String] = Credentials.byName(name).asJava
}
