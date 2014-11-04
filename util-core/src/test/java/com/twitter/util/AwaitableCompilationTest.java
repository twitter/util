package com.twitter.util;

import junit.framework.Assert;
import org.junit.Test;
import scala.runtime.BoxedUnit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class AwaitableCompilationTest {

  public static class MockAwaitable implements Awaitable<String> {
    @Override
    public Awaitable<String> ready(Duration timeout, CanAwait permit) throws TimeoutException {
      return this;
    }

    @Override
    public String result(Duration timeout, CanAwait permit) throws Exception {
      return "42";
    }

    @Override
    public boolean isReady(CanAwait permit) {
      return true;
    }
  }

  public static class MockCloseAwaitably extends AbstractCloseAwaitably {
    @Override
    public Future<BoxedUnit> onClose(Time deadline) {
      return Future.Done();
    }

    public int fortyTwo() {
      return 42;
    }
  }

  @Test
  public void testAwaitable() throws Exception {
    Awaitable<String> awaitable = new MockAwaitable();
    Assert.assertEquals(awaitable, Await.ready(awaitable));
    Assert.assertEquals("42", Await.result(awaitable));
  }

  @Test
  public void testCloseAwaitable() throws Exception {
    MockCloseAwaitably m = new MockCloseAwaitably();
    m.close();
    Await.ready(m);
    Assert.assertEquals(42, m.fortyTwo());
  }

  @Test
  public void testAll() throws TimeoutException, InterruptedException {
    MockAwaitable a = new MockAwaitable();
    MockAwaitable b = new MockAwaitable();
    MockAwaitable c = new MockAwaitable();

    Await.all(a, b, c);

    Collection<Awaitable<?>> all = new ArrayList<Awaitable<?>>(Arrays.asList(a, b, c));
    Await.all(all, Duration.Top());
  }
}
