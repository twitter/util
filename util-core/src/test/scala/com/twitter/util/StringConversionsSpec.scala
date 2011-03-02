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

import org.specs.Specification
import com.twitter.conversions.string._

class StringConversionsSpec extends Specification {
  "string" should {
    "quoteC" in {
      "nothing".quoteC mustEqual "nothing"
      "name\tvalue\t\u20acb\u00fcllet?\u20ac".quoteC mustEqual "name\\tvalue\\t\\u20acb\\xfcllet?\\u20ac"
      "she said \"hello\"".quoteC mustEqual "she said \\\"hello\\\""
      "\\backslash".quoteC  mustEqual "\\\\backslash"
    }

    "unquoteC" in {
      "nothing".unquoteC mustEqual "nothing"
      "name\\tvalue\\t\\u20acb\\xfcllet?\\u20ac".unquoteC  mustEqual "name\tvalue\t\u20acb\u00fcllet?\u20ac"
      "she said \\\"hello\\\"".unquoteC mustEqual "she said \"hello\""
      "\\\\backslash".unquoteC mustEqual "\\backslash"
      "real\\$dollar".unquoteC mustEqual "real\\$dollar"
      "silly\\/quote".unquoteC mustEqual "silly/quote"
    }

    "hexlify" in {
      "hello".getBytes.slice(1, 4).hexlify mustEqual "656c6c"
      "hello".getBytes.hexlify mustEqual "68656c6c6f"
    }

    "unhexlify" in {
      "656c6c".unhexlify.toList mustEqual "hello".getBytes.slice(1, 4).toList
      "68656c6c6f".unhexlify.toList mustEqual "hello".getBytes.toList
    }
  }
}
