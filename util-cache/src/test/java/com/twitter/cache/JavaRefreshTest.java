package com.twitter.cache;

import com.twitter.util.Await;
import com.twitter.util.Duration;
import com.twitter.util.Future;

import static org.junit.Assert.*;
import org.junit.Test;

import scala.Function0;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

public class JavaRefreshTest {

  @Test
  public void testRefreshWorksFromJava() throws Exception {
    @SuppressWarnings("unchecked")
    Function0<Future<Integer>> provider = mock(Function0.class);
    when(provider.apply()).thenReturn(Future.value(1));
    Function0<Future<Integer>> memoized = Refresh.every(Duration.fromSecondsJ(10), provider);

    assertEquals(1, Await.result(memoized.apply()).intValue());
    assertEquals(1, Await.result(memoized.apply()).intValue());
    verify(provider, times(1)).apply();
  }

}
