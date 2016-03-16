/* Copyright 2016 Twitter, Inc. */
package com.twitter.util;

import org.junit.Assert;
import org.junit.Test;

public class ThrowablesCompilationTest {

  /**
   * Note the lack of `throws Excepton` on the method declaration.
   */
  @Test
  public void testUnchecked() {
    Future<String> future = Future.value("hi");
    try {
      String s = Await.result(future);
      Assert.assertEquals("hi", s);
    } catch (Exception e) {
      Throwables.unchecked(e);
    }
  }

}
