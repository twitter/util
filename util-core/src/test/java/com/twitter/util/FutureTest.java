package com.twitter.util;

import junit.framework.TestCase;

/**
 * Tests are not currently run for java, but for our purposes, if the test compiles at all, it's
 * a success.
 */
public class FutureTest extends TestCase {
  public void testFutureCastMap() {
    Future<String> f = Future$.MODULE$.value("23");
    Future<Integer> f2 = f.map(new Function<String, Integer>() {
      public Integer apply(String in) {
        return Integer.parseInt(in);
      }
    });
    assertEquals((int) f2.get(), 23);
  }

  public void testFutureCastFlatMap() {
    Future<String> f = Future$.MODULE$.value("23");
    Future<Integer> f2 = f.flatMap(new Function<String, Future<Integer>>() {
      public Future<Integer> apply(String in) {
        return Future$.MODULE$.value(Integer.parseInt(in));
      }
    });
    assertEquals((int) f2.get(), 23);
  }
}
