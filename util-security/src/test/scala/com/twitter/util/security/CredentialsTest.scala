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

import org.scalatestplus.scalacheck.Checkers
import org.scalatest.funsuite.AnyFunSuite

class CredentialsTest extends AnyFunSuite with Checkers {
  test("parse a simple auth file") {
    val content = "username: root\npassword: not_a_password\n"
    val result = Credentials(content)
    assert(result == Map("username" -> "root", "password" -> "not_a_password"))
  }

  test("parse a more complex auth file") {
    val content = """# random comment

username: root
password: not_a-real/pr0d:password
# more stuff
moar: ok

        """
    assert(
      Credentials(content) == Map(
        "username" -> "root",
        "password" -> "not_a-real/pr0d:password",
        "moar" -> "ok"
      )
    )
  }

  test("work for java peeps too") {
    val content = "username: root\npassword: not_a_password\n"
    val jmap = new Credentials().read(content)
    assert(jmap.size() == 2)
    assert(jmap.get("username") == "root")
    assert(jmap.get("password") == "not_a_password")
  }

  test("handle \r\n line breaks") {
    val content = "username: root\r\npassword: not_a_password\r\n"
    assert(Credentials(content) == Map("username" -> "root", "password" -> "not_a_password"))
  }

  test("handle special chars") {
    val pass = (32 to 126)
      .map(_.toChar)
      .filter(c => c != '\r' && c != '\n' && c != '\'')
      .mkString
    val content = s"username: root\npassword: '$pass'\n"
    assert(Credentials(content) == Map("username" -> "root", "password" -> pass))
  }
}
