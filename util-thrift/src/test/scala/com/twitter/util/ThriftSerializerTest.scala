/*
 * Copyright 2010 Twitter Inc.
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

package com.twitter.util

import org.scalatest.wordspec.AnyWordSpec

class ThriftSerializerTest extends AnyWordSpec {
  val aString = "me gustan los tacos y los burritos"
  val aNumber = 42
  val original = new TestThriftStructure(aString, aNumber)
  val json = """{"aString":"%s","aNumber":%d}""".format(aString, aNumber)
  val encodedBinary = Some("CwABAAAAIm1lIGd1c3RhbiBsb3MgdGFjb3MgeSBsb3MgYnVycml0b3MIAAIAAAAqAA==")
  val encodedCompact = Some("GCJtZSBndXN0YW4gbG9zIHRhY29zIHkgbG9zIGJ1cnJpdG9zFVQA")

  def testSerializer(serializer: ThriftSerializer, stringVersion: Option[String] = None) = {
    val bytes = serializer.toBytes(original)
    var obj = new TestThriftStructure()
    serializer.fromBytes(obj, bytes)
    assert(obj.aString == original.aString)
    assert(obj.aNumber == original.aNumber)

    stringVersion match {
      case None =>
      case Some(str) =>
        assert(serializer.toString(original) == str)
        obj = new TestThriftStructure()
        serializer.fromString(obj, str)
        assert(obj.aString == original.aString)
        assert(obj.aNumber == original.aNumber)
    }
    true
  }

  "ThriftSerializer" should {
    "encode and decode json" in {
      testSerializer(new JsonThriftSerializer, Some(json))
    }

    "encode and decode binary" in {
      testSerializer(new BinaryThriftSerializer, encodedBinary)
    }

    "encode and decode compact" in {
      testSerializer(new CompactThriftSerializer, encodedCompact)
    }
  }
}
