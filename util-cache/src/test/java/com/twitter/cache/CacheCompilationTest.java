/* Copyright 2015 Twitter, Inc. */
package com.twitter.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.twitter.util.Await;
import com.twitter.util.Function;
import com.twitter.util.Future;
import org.junit.Assert;
import org.junit.Test;
import scala.Function1;

public class CacheCompilationTest {

  @Test
  public void testFromMap() throws Exception {
    ConcurrentMap<String, Future<String>> map = new ConcurrentHashMap<>();

    Function1<String, Future<String>> fn = new Function<String, Future<String>>() {
      public Future<String> apply(String s) {
        return Future.value(s);
      }
    };

    Function1<String, Future<String>> fromMap = FutureCache.fromMap(fn, map);
    Assert.assertEquals("1", Await.result(fromMap.apply("1")));
  }

  @Test
  public void testStandard() throws Exception {
    ConcurrentMap<String, Future<String>> map = new ConcurrentHashMap<>();
    ConcurrentMapCache<String, String> mapCache = new ConcurrentMapCache<>(map);

    Function1<String, Future<String>> fn = new Function<String, Future<String>>() {
      public Future<String> apply(String s) {
        return Future.value(s);
      }
    };

    Function1<String, Future<String>> std = FutureCache.standard(fn, mapCache);
    Assert.assertEquals("1", Await.result(std.apply("1")));
  }

}
