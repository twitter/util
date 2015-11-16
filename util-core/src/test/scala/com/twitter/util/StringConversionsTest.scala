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

package com.twitter.util

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner

import com.twitter.conversions.string._

@RunWith(classOf[JUnitRunner])
class StringConversionsTest extends WordSpec {
  "string" should {
    "quoteC" in {
      assert("nothing".quoteC == "nothing")
      assert("name\tvalue\t\u20acb\u00fcllet?\u20ac".quoteC == "name\\tvalue\\t\\u20acb\\xfcllet?\\u20ac")
      assert("she said \"hello\"".quoteC == "she said \\\"hello\\\"")
      assert("\\backslash".quoteC  == "\\\\backslash")
    }

    "unquoteC" in {
      assert("nothing".unquoteC == "nothing")
      assert("name\\tvalue\\t\\u20acb\\xfcllet?\\u20ac".unquoteC  == "name\tvalue\t\u20acb\u00fcllet?\u20ac")
      assert("she said \\\"hello\\\"".unquoteC == "she said \"hello\"")
      assert("\\\\backslash".unquoteC == "\\backslash")
      assert("real\\$dollar".unquoteC == "real\\$dollar")
      assert("silly\\/quote".unquoteC == "silly/quote")
    }

    "hexlify" in {
      assert("hello".getBytes.slice(1, 4).hexlify == "656c6c")
      assert("hello".getBytes.hexlify == "68656c6c6f")
    }

    "unhexlify" in {
      assert("656c6c".unhexlify.toList == "hello".getBytes.slice(1, 4).toList)
      assert("68656c6c6f".unhexlify.toList == "hello".getBytes.toList)
      "5".unhexlify
      assert("5".unhexlify.hexlify.toInt == 5)
    }
  }
}
