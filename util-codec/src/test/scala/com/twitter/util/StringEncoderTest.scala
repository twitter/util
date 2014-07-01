package com.twitter.util

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

import java.lang.StringBuilder

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner

class TestBase64Encoder extends Base64StringEncoder {
}

@RunWith(classOf[JUnitRunner])
class StringEncoderTest extends WordSpec {
  val longString = "A string that is really really really really really really long and has more than 76 characters"
  val result = "QSBzdHJpbmcgdGhhdCBpcyByZWFsbHkgcmVhbGx5IHJlYWxseSByZWFsbHkgcmVhbGx5IHJlYWxseSBsb25nIGFuZCBoYXMgbW9yZSB0aGFuIDc2IGNoYXJhY3RlcnM="
  val testEncoder = new TestBase64Encoder()

  "strip new lines" in {
    assert(testEncoder.encode(longString.getBytes) === result)
  }

  "decode value with stripped new lines" in {
    assert(new String(testEncoder.decode(result)) === longString)
  }
}

@RunWith(classOf[JUnitRunner])
class GZIPStringEncoderTest extends WordSpec {
  "a gzip string encoder" should {
    val gse = new GZIPStringEncoder {}
    "properly encode and decode strings" in {
      def testCodec(str: String) {
        assert(str === gse.decodeString(gse.encodeString(str)))
      }

      testCodec("a")
      testCodec("\n\t\n\t\n\n\n\n\t\n\nt\n\t\n\t\n\tn\t\nt\nt\nt\nt\nt\nt\tn\nt\nt\n\t\nt\n")
      testCodec("aosnetuhsaontehusaonethsoantehusaonethusonethusnaotehu")

      // build a huge string
      val sb = new StringBuilder
      for (_ <- 1 to 10000) {
        sb.append("oasnuthoesntihosnteidosentidosentauhsnoetidosentihsoneitdsnuthsin\n")
      }
      testCodec(sb.toString)
    }
  }
}
