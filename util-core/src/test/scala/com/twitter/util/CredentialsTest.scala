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

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.Checkers

@RunWith(classOf[JUnitRunner])
class CredentialsTest extends FunSuite with Checkers {
  test("parse a simple auth file") {
      val content = "username: root\npassword: hellokitty\n"
      assert(Credentials(content) == Map("username" -> "root", "password" -> "hellokitty"))
  }

  test("parse a more complex auth file") {
      val content = """# random comment

username:root
password  : last_0f-the/international:playboys
# more stuff
   moar :ok

        """
      assert(Credentials(content) == Map(
        "username" -> "root",
        "password" -> "last_0f-the/international:playboys",
        "moar" -> "ok"
      ))
  }

  test("work for java peeps too") {
    val content = "username: root\npassword: hellokitty\n"
    val jmap = new Credentials().read(content)
    assert(jmap.size() == 2)
    assert(jmap.get("username") == "root")
    assert(jmap.get("password") == "hellokitty")
  }

  test("handle \r\n line breaks") {
    val content = "username: root\r\npassword: hellokitty\r\n"
    assert(Credentials(content) == Map("username" -> "root", "password" -> "hellokitty"))
  }

  test("handle special chars") {
    val pass = (0 to 127)
      .map(_.toChar)
      .filter(c => c != '\r' && c != '\n')
      .mkString
    val content = s"username: root\npassword: $pass\n"
    assert(Credentials(content) == Map("username" -> "root", "password" -> pass))
  }
}
