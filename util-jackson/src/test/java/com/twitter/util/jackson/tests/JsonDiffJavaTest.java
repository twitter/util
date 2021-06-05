package com.twitter.util.jackson.tests;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import scala.Option;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.Assert;
import org.junit.Test;

import com.twitter.util.jackson.JsonDiff;

public class JsonDiffJavaTest extends Assert {

  @Test
  public void testDiff() {
    String a = "{\"a\": 1, \"b\": 2}";
    String b = "{\"b\": 2, \"a\": 1}";

    Option<JsonDiff.Result> result = JsonDiff.diff(a, b);
    assertFalse(result.isDefined());
  }

  @Test
  public void testDiffWithNormalizer() {
    String a = "{\"a\": 1, \"b\": 2, \"c\": 5}";
    String b = "{\"b\": 2, \"a\": 1, \"c\": 3}";

    // normalizerFn will "normalize" the value for "c" into the expected value
    Option<JsonDiff.Result> result = JsonDiff.diff(a, b, (JsonNode jsonNode) -> ((ObjectNode)jsonNode).put("c", 5) );
    assertFalse(result.isDefined());
  }

  @Test
  public void testAssertDiff() {
    String a = "{\"a\": 1, \"b\": 2}";
    String b = "{\"b\": 2, \"a\": 1}";

    JsonDiff.assertDiff(a, b);
  }

  @Test
  public void testAssertDiffWithNormalizer() {
    String a = "{\"a\": 1, \"b\": 2, \"c\": 5}";
    String b = "{\"b\": 2, \"a\": 1, \"c\": 3}";

    // normalizerFn will "normalize" the value for "c" into the expected value
    JsonDiff.assertDiff(a, b, (JsonNode jsonNode) -> ((ObjectNode)jsonNode).put("c", 5) );
  }

  @Test
  public void testAssertDiffWithPrintStream() throws Exception {
    String a = "{\"a\": 1, \"b\": 2}";
    String b = "{\"b\": 2, \"a\": 1}";

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos, true, "utf-8");

    try {
      JsonDiff.assertDiff(a, b, ps);
    } finally {
      ps.flush();
      ps.close();
    }
  }

  @Test
  public void testAssertDiffWithNormalizerAndPrintStream() throws Exception {
    String a = "{\"a\": 1, \"b\": 2, \"c\": 5}";
    String b = "{\"b\": 2, \"a\": 1, \"c\": 3}";

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos, true, "utf-8");

    try {
      // normalizerFn will "normalize" the value for "c" into the expected value
      JsonDiff.assertDiff(a, b, (JsonNode jsonNode) -> ((ObjectNode)jsonNode).put("c", 5) );
    } finally {
      ps.flush();
      ps.close();
    }
  }
}
