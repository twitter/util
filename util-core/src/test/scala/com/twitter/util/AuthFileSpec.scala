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

import org.specs.Specification

object AuthFileSpec extends Specification {
  "AuthFile" should {
    "parse a simple auth file" in {
      val content = "username: root\npassword: hellokitty\n"
      AuthFile(content) mustEqual Map("username" -> "root", "password" -> "hellokitty")
    }

    "parse a more complex auth file" in {
      val content = """# random comment

username:root
password  : last_0f-the/international:playboys
# more stuff
   moar :ok

        """
      AuthFile(content) mustEqual Map(
        "username" -> "root",
        "password" -> "last_0f-the/international:playboys",
        "moar" -> "ok"
      )
    }

    "work for java peeps too" in {
      val content = "username: root\npassword: hellokitty\n"
      val jmap = new AuthFile().read(content)
      jmap.size() mustEqual 2
      jmap.get("username") mustEqual "root"
      jmap.get("password") mustEqual "hellokitty"
    }
  }
}
