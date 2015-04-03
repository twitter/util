/* Copyright 2015 Twitter, Inc. */
package com.twitter.concurrent;

import com.twitter.util.Function0;
import com.twitter.util.Future;
import org.junit.Test;

public class AsyncSemaphoreCompilationTest {

  private Future<String> limited() {
    return Future.value("hello");
  }

  @Test
  public void testAcquireAndRelease() {
    AsyncSemaphore semaphore = new AsyncSemaphore(5);
    semaphore.acquireAndRun(new Function0<Future<String>>() {
      public Future<String> apply() {
        return limited();
      }
    });
  }

}
