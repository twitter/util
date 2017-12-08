package com.twitter.cache.guava;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.twitter.util.Await;
import com.twitter.util.Future;
import org.junit.Assert;
import org.junit.Test;
import scala.Function1;

public class GuavaCacheCompilationTest {

  @Test
  public void testGuava() throws Exception {
    CacheLoader<String, Future<String>> loader =
        new CacheLoader<String, Future<String>>() {
      @Override
      public Future<String> load(String s) throws Exception {
        return Future.value(s);
      }
    };

    LoadingCache<String, Future<String>> guava = CacheBuilder.newBuilder()
        .build(loader);

    Function1<String, Future<String>> futureCache =
        GuavaCache.fromLoadingCache(guava);

    Future<String> value = futureCache.apply("key");
    Assert.assertEquals("key", Await.result(value));
  }

}
