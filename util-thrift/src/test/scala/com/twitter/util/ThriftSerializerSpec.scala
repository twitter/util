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

package com.twitter.util

import org.specs.Specification
import org.specs.specification.PendingUntilFixed

object ThriftSerializerSpec extends Specification with PendingUntilFixed {
  val aString = "me gustan los tacos y los burritos"
  val aNumber = 42
  val original = new TestThriftStructure(aString, aNumber)
  val json = """{"aString":"%s","aNumber":%d}""".format(aString, aNumber)

  def testBinarySerializer(serializer: ThriftSerializer) = {
    val bytes = serializer.toBytes(original)
    val obj = new TestThriftStructure()
    serializer.fromBytes(obj, bytes)
    obj.aString mustEqual original.aString
    obj.aNumber mustEqual original.aNumber
  }


  "ThriftSerializer" should {
    "encode JSON" in {
      val serializer = new JsonThriftSerializer
      serializer.toString(original) mustEqual json
    }

    "decode JSON" in {
      pendingUntilFixed {
        val serializer = new JsonThriftSerializer
        val obj = new TestThriftStructure
        serializer.fromString(obj, json)
        obj.aString must notBeNull
        obj.aString mustEqual aString
        obj.aNumber mustEqual aNumber
      }
    }

    "encode and decode binary" in {
      testBinarySerializer(new BinaryThriftSerializer)
    }

    "encode and decode compact" in {
      testBinarySerializer(new CompactThriftSerializer)
    }
  }
}
