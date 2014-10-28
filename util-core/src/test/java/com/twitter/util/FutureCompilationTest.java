package com.twitter.util;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import scala.Some;
import scala.Tuple2;

public class FutureCompilationTest {
  @Test
  public void testFutureCastMap() throws Exception {
    Future<String> f = Future.value("23");
    Future<Integer> f2 = f.map(new Function<String, Integer>() {
      public Integer apply(String in) {
        return Integer.parseInt(in);
      }
    });
    Assert.assertEquals((int) Await.result(f2), 23);
  }

  @Test
  public void testFutureCastFlatMap() throws Exception {
    Future<String> f = Future.value("23");
    Future<Integer> f2 = f.flatMap(new Function<String, Future<Integer>>() {
      public Future<Integer> apply(String in) {
        return Future.value(Integer.parseInt(in));
      }
    });
    Assert.assertEquals((int) Await.result(f2), 23);
  }

  @Test
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
    Assert.assertEquals((int) Await.result(f2), 23);
  }

  @Test
  public void testJoin() throws Exception {
    Future<String> f = Future.value("23");
    Future<Integer> f2 = Future.value(3);
    Some<Return<Tuple2<String, Integer>>> expected = new Some<Return<Tuple2<String, Integer>>>(new Return<Tuple2<String, Integer>>(new Tuple2<String, Integer>("23", 3)));
    Assert.assertEquals(Futures.join(f, f2).poll(), expected);
  }

  @Test
  public void testCollect() throws Exception {
    Future<String> f = Future.value("23");
    Future<String> f2 = Future.value("33");
    Some<Return<List<String>>> expected = new Some<Return<List<String>>>(new Return<List<String>>(Arrays.asList("23", "33")));
    Assert.assertEquals(Future.collect(Arrays.asList(f, f2)).poll(), expected);
  }

  @Test
  public void testFlatten() throws Exception {
    Future<Future<String>> f = Future.value(Future.value("23"));
    Future<String> f2 = Future.value("23");
    Assert.assertEquals(Futures.flatten(f).poll(), f2.poll());
  }
}
