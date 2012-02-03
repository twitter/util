package com.twitter.util;

import junit.framework.TestCase;

/**
 * Tests are not currently run for java, but for our purposes, if the test compiles at all, it's
 * a success.
 */
public class FunctionTest extends TestCase {
  /** Confirm that we can extend ExceptionalFunction with applyE(). */
  public void testDefineWithException() {
    ExceptionalFunction<Integer, String> fun = new ExceptionalFunction<Integer, String>() {
      @Override
      public String applyE(Integer in) throws Exception {
        throw new Exception("Expected");
      }
    };
    try {
      fun.apply(1);
      assert false : "Should have thrown";
    } catch (Exception e) {
      // pass: expected
    }
  }

  /** Confirm that we can extend ExceptionalFunction with applyE(). */
  public void testExceptionalFunction0() {
    ExceptionalFunction0<Integer> fun = new ExceptionalFunction0<Integer>() {
      @Override
      public Integer applyE() throws Exception {
        throw new Exception("Expected");
      }
    };
    try {
      fun.apply();
      assert false : "Should have thrown";
    } catch (Exception e) {
      // pass: expected
    }
  }

}
