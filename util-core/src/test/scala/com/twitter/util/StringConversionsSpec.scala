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

import org.scalatest.{WordSpec, Matchers}
import com.twitter.conversions.string._

class StringConversionsSpec extends WordSpec with Matchers {
  "string" should  {
    "quoteC" in {
      "nothing".quoteC shouldEqual "nothing"
      "name\tvalue\t\u20acb\u00fcllet?\u20ac".quoteC shouldEqual "name\\tvalue\\t\\u20acb\\xfcllet?\\u20ac"
      "she said \"hello\"".quoteC shouldEqual "she said \\\"hello\\\""
      "\\backslash".quoteC  shouldEqual "\\\\backslash"
    }

    "unquoteC" in {
      "nothing".unquoteC shouldEqual "nothing"
      "name\\tvalue\\t\\u20acb\\xfcllet?\\u20ac".unquoteC  shouldEqual "name\tvalue\t\u20acb\u00fcllet?\u20ac"
      "she said \\\"hello\\\"".unquoteC shouldEqual "she said \"hello\""
      "\\\\backslash".unquoteC shouldEqual "\\backslash"
      "real\\$dollar".unquoteC shouldEqual "real\\$dollar"
      "silly\\/quote".unquoteC shouldEqual "silly/quote"
    }

    "hexlify" in {
      "hello".getBytes.slice(1, 4).hexlify shouldEqual "656c6c"
      "hello".getBytes.hexlify shouldEqual "68656c6c6f"
    }

    "unhexlify" in {
      "656c6c".unhexlify.toList shouldEqual "hello".getBytes.slice(1, 4).toList
      "68656c6c6f".unhexlify.toList shouldEqual "hello".getBytes.toList
      "5".unhexlify
      "5".unhexlify.hexlify.toInt shouldEqual 5
    }
  }
}
