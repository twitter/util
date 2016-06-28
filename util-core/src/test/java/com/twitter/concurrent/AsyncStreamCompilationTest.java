package com.twitter.concurrent;

import com.twitter.concurrent.AsyncStream;
import com.twitter.util.Await;
import com.twitter.util.Function;
import com.twitter.util.Function0;
import org.junit.Test;

public class AsyncStreamCompilationTest {
  @Test
  public void testErgo() throws Exception {
    final AsyncStream<Integer> a = AsyncStream.<Integer>empty();
    Boolean emptyA = (Boolean) Await.result(a.isEmpty());
    assert(emptyA);

    final AsyncStream<Integer> b = AsyncStream.<Integer>mk(
      1,
      new Function0<AsyncStream<Integer>>() {
        @Override
        public AsyncStream<Integer> apply() {
          return a;
        }
      }
    );

    Boolean emptyB = (Boolean) Await.result(b.isEmpty());
    assert(!emptyB);

    b.map(new Function<Integer, Integer>() {
      @Override
      public Integer apply(Integer i) {
        return i + 1;
      }
    });

    assert(Await.result(b.head()).get() == 2);

    AsyncStream<Integer> c = b.concat(
      new Function0<AsyncStream<Integer>>() {
        @Override public
        AsyncStream<Integer> apply() {
          return AsyncStream.of(3);
        }
      }
    );

    AsyncStream.flattens(AsyncStream.of(AsyncStream.of(1)));

    AsyncStream<Integer> merged = AsyncStream.merge(
        AsyncStream.<Integer>of(1),
        AsyncStream.<Integer>of(2),
        AsyncStream.<Integer>of(3)
    );

  }
}
