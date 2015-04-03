package com.twitter.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import scala.Tuple3;
import scala.runtime.BoxedUnit;

public class FutureCompilationTest {
  @Test
  public void testFutureCastMap() throws Exception {
    Future<String> a = Future.value("23");
    Future<Integer> b = a.map(new Function<String, Integer>() {
      public Integer apply(String in) {
        return Integer.parseInt(in);
      }
    });

    Assert.assertEquals(23, (int) Await.result(b));
  }

  @Test
  public void testFutureCastFlatMap() throws Exception {
    Future<String> a = Future.value("23");
    Future<Integer> b = a.flatMap(new Function<String, Future<Integer>>() {
      public Future<Integer> apply(String in) {
        return Future.value(Integer.parseInt(in));
      }
    });

    Assert.assertEquals(23, (int) Await.result(b));
  }

  @Test
  public void testTransformedBy() throws Exception {
    Future<String> a = Future.value("23");
    Future<Integer> b = a.transformedBy(new FutureTransformer<String, Integer>() {
      public Future<Integer> flatMap(String value) {
        return Future.value(Integer.parseInt(value));
      }

      public Future<Integer> rescue(Throwable throwable) {
        return Future.value(0);
      }
    });

    Assert.assertEquals(23, (int) Await.result(b));
  }

  @Test
  public void testJoin3() throws Exception {
    Future<String> a = Future.value("23");
    Future<Integer> b = Future.value(3);
    Future<String> c = Future.value("44");

    Assert.assertEquals(new Tuple3<String, Integer, String>("23", 3, "44"), Await.result(Futures.join(a, b, c)));
  }

  @Test
  public void testJoinList() throws Exception {
    Future<String> a = Future.value("23");
    Future<String> b = Future.value("33");

    Assert.assertEquals(BoxedUnit.UNIT, Await.result(Futures.join(Arrays.asList(a, b))));
  }

  @Test
  public void testCollect() throws Exception {
    Future<String> a = Future.value("23");
    Future<String> b = Future.value("33");

    Assert.assertEquals(Arrays.asList("23", "33"), Await.result(Futures.collect(Arrays.asList(a, b))));
  }

  @Test
  public void testFlatten() throws Exception {
    Future<Future<String>> a = Future.value(Future.value("23"));

    Assert.assertEquals("23", Await.result(Futures.flatten(a)));
  }

  @Test
  public void testSelect() throws Exception {
    Future<String> a = Future.value("23");
    Future<String> b = Future.value("33");

    Assert.assertEquals(new Return<String>("23"), Await.result(Futures.select(Arrays.asList(a, b)))._1());
  }

  @Test
  public void testCollectMap() throws Exception {
    Map<String, Future<String>> a = new HashMap<String, Future<String>>();
    a.put("1", Future.value("1"));
    a.put("2", Future.value("2"));

    Map<String, String> b = Await.result(Futures.collect(a));

    Assert.assertEquals("1", b.get("1"));
    Assert.assertEquals("2", b.get("2"));
  }
}