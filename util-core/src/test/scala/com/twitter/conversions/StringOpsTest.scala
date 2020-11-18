/*
 * Copyright 2010 Twitter, Inc.
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

package com.twitter.conversions

import com.twitter.conversions.StringOps._
import org.scalatest.funsuite.AnyFunSuite

class StringOpsTest extends AnyFunSuite {

  test("string#quoteC") {
    assert("nothing".quoteC == "nothing")
    assert(
      "name\tvalue\t\u20acb\u00fcllet?\u20ac".quoteC == "name\\tvalue\\t\\u20acb\\xfcllet?\\u20ac"
    )
    assert("she said \"hello\"".quoteC == "she said \\\"hello\\\"")
    assert("\\backslash".quoteC == "\\\\backslash")
  }

  test("string#unquoteC") {
    assert("nothing".unquoteC == "nothing")
    assert(
      "name\\tvalue\\t\\u20acb\\xfcllet?\\u20ac".unquoteC == "name\tvalue\t\u20acb\u00fcllet?\u20ac"
    )
    assert("she said \\\"hello\\\"".unquoteC == "she said \"hello\"")
    assert("\\\\backslash".unquoteC == "\\backslash")
    assert("real\\$dollar".unquoteC == "real\\$dollar")
    assert("silly\\/quote".unquoteC == "silly/quote")
  }

  test("string#hexlify") {
    assert("hello".getBytes.slice(1, 4).hexlify == "656c6c")
    assert("hello".getBytes.hexlify == "68656c6c6f")
  }

  test("string#unhexlify") {
    assert("656c6c".unhexlify.toList == "hello".getBytes.slice(1, 4).toList)
    assert("68656c6c6f".unhexlify.toList == "hello".getBytes.toList)
    "5".unhexlify
    assert("5".unhexlify.hexlify.toInt == 5)
  }

  test("string#toCamelCase") {
    assert("foo_bar".toCamelCase == "fooBar")

    assert(toCamelCase("foo_bar") == "fooBar")
    assert(toPascalCase("foo_bar") == "FooBar")

    assert(toCamelCase("foo__bar") == "fooBar")
    assert(toPascalCase("foo__bar") == "FooBar")

    assert(toCamelCase("foo___bar") == "fooBar")
    assert(toPascalCase("foo___bar") == "FooBar")

    assert(toCamelCase("_foo_bar") == "fooBar")
    assert(toPascalCase("_foo_bar") == "FooBar")

    assert(toCamelCase("foo_bar_") == "fooBar")
    assert(toPascalCase("foo_bar_") == "FooBar")

    assert(toCamelCase("_foo_bar_") == "fooBar")
    assert(toPascalCase("_foo_bar_") == "FooBar")

    assert(toCamelCase("a_b_c_d") == "aBCD")
    assert(toPascalCase("a_b_c_d") == "ABCD")
  }

  test("string#toPascalCase") {
    assert("foo_bar".toPascalCase == "FooBar")
  }

  test("string#toSnakeCase") {
    assert("FooBar".toSnakeCase == "foo_bar")
  }

  test("string#toPascalCase return an empty string if given null") {
    assert(toPascalCase(null) == "")
  }

  test("string#toPascalCase leave a CamelCased name untouched") {
    assert(toPascalCase("GetTweets") == "GetTweets")
    assert(toPascalCase("FooBar") == "FooBar")
    assert(toPascalCase("HTML") == "HTML")
    assert(toPascalCase("HTML5") == "HTML5")
    assert(toPascalCase("Editor2TOC") == "Editor2TOC")
  }

  test("string#toCamelCase Method") {
    assert(toCamelCase("GetTweets") == "getTweets")
    assert(toCamelCase("FooBar") == "fooBar")
    assert(toCamelCase("HTML") == "hTML")
    assert(toCamelCase("HTML5") == "hTML5")
    assert(toCamelCase("Editor2TOC") == "editor2TOC")
  }

  test("string#toPascalCase & toCamelCase Method function") {
    // test both produce empty strings
    toPascalCase(null).isEmpty && toCamelCase(null).isEmpty

    val name = "emperor_norton"
    // test that the first letter for toCamelCase is lower-cased
    assert(toCamelCase(name).toList.head.isLower)
    // test that toPascalCase and toCamelCase.capitalize are the same
    assert(toPascalCase(name) == toCamelCase(name).capitalize)
  }

  test("string#toSnakeCase replace upper case with underscore") {
    assert(toSnakeCase("MyCamelCase") == "my_camel_case")
    assert(toSnakeCase("CamelCase") == "camel_case")
    assert(toSnakeCase("Camel") == "camel")
    assert(toSnakeCase("MyCamel12Case") == "my_camel12_case")
    assert(toSnakeCase("CamelCase12") == "camel_case12")
    assert(toSnakeCase("Camel12") == "camel12")
    assert(toSnakeCase("Foobar") == "foobar")
  }

  test("string#toSnakeCase not modify existing snake case strings") {
    assert(toSnakeCase("my_snake_case") == "my_snake_case")
    assert(toSnakeCase("snake") == "snake")
  }

  test("string#toSnakeCase handle abbeviations") {
    assert(toSnakeCase("ABCD") == "abcd")
    assert(toSnakeCase("HTML") == "html")
    assert(toSnakeCase("HTMLEditor") == "html_editor")
    assert(toSnakeCase("EditorTOC") == "editor_toc")
    assert(toSnakeCase("HTMLEditorTOC") == "html_editor_toc")

    assert(toSnakeCase("HTML5") == "html5")
    assert(toSnakeCase("HTML5Editor") == "html5_editor")
    assert(toSnakeCase("Editor2TOC") == "editor2_toc")
    assert(toSnakeCase("HTML5Editor2TOC") == "html5_editor2_toc")
  }

  test("string#toOption when nonEmpty") {
    assert(toOption("foo") == Some("foo"))
  }

  test("string#toOption when empty") {
    assert(toOption("") == None)
    assert(toOption(null) == None)
  }

  test("string#getOrElse when nonEmpty") {
    assert(getOrElse("foo", "purple") == "foo")
  }

  test("string#getOrElse when empty") {
    assert(getOrElse("", "purple") == "purple")
    assert(getOrElse(null, "purple") == "purple")
  }
}
