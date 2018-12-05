/*
 * Copyright 2010 Twitter Inc.
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

package com.twitter.conversions

import java.util.concurrent.Callable
import scala.language.implicitConversions

/**
 * Implicits for turning a block of code into a Runnable or Callable.
 */
@deprecated("Use `com.twitter.conversions.ThreadOps`", "2018-12-05")
object thread {
  implicit def makeRunnable(f: => Unit): Runnable = new Runnable() { def run(): Unit = f }

  implicit def makeCallable[T](f: => T): Callable[T] = new Callable[T]() { def call(): T = f }
}
