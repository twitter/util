package com.twitter.util;

import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FuturePoolCompilationTest {

  @Test
  public void testFuturePools() {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    FuturePool a = FuturePools.IMMEDIATE_POOL;
    FuturePool b = FuturePools.unboundedPool();
    FuturePool c = FuturePools.interruptibleUnboundedPool();
    FuturePool d = FuturePools.newFuturePool(executor);
    FuturePool e = FuturePools.newInterruptibleFuturePool(executor);
  }
}
