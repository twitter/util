package com.twitter.util;

import junit.framework.TestCase;

/**
 * Tests are not currently run for java, but for our purposes, if the test compiles at all, it's
 * a success.
 */
public class FutureTest extends TestCase {
  public void testFutureCastMap() throws Exception {
    Future<String> f = Future.value("23");
    Future<Integer> f2 = f.map(new Function<String, Integer>() {
      public Integer apply(String in) {
        return Integer.parseInt(in);
      }
    });
    assertEquals((int) Await.result(f2), 23);
  }

  public void testFutureCastFlatMap() throws Exception {
    Future<String> f = Future.value("23");
    Future<Integer> f2 = f.flatMap(new Function<String, Future<Integer>>() {
      public Future<Integer> apply(String in) {
        return Future.value(Integer.parseInt(in));
      }
    });
    assertEquals((int) Await.result(f2), 23);
  }

  public void testTransformedBy() throws Exception {
    Future<String> f = Future.value("23");
    Future<Integer> f2 = f.transformedBy(new FutureTransformer<String, Integer>() {
      public Future<Integer> flatMap(String value) {
        return Future.value(Integer.parseInt(value));
      }
      public Future<Integer> rescue(Throwable throwable) {
        return Future.value(0);
      }
    });
    assertEquals((int) Await.result(f2), 23);
  }
}
