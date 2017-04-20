package com.twitter.util;

import org.junit.Assert;
import org.junit.Test;
import scala.runtime.BoxedUnit;

import static com.twitter.util.Function.cons;
import static com.twitter.util.Function.func;
import static com.twitter.util.Function.func0;
import static com.twitter.util.Function.excons;
import static com.twitter.util.Function.exfunc;

public class FunctionCompilationTest {

  /** Confirm that we can extend ExceptionalFunction with applyE(). */
  @Test(expected = Exception.class)
  public void testDefineWithException() {
    ExceptionalFunction<Integer, String> fun = new ExceptionalFunction<Integer, String>() {
      @Override
      public String applyE(Integer in) throws Exception {
        throw new Exception("Expected");
      }
    };
    fun.apply(1);
  }

  /** Confirm that we can extend ExceptionalFunction0 with applyE(). */
  @Test(expected = Exception.class)
  public void testExceptionalFunction0() {
    ExceptionalFunction0<Integer> fun = new ExceptionalFunction0<Integer>() {
      @Override
      public Integer applyE() throws Exception {
        throw new Exception("Expected");
      }
    };
    fun.apply();
  }

  @Test
  public void testMakeFunctionFromLambda() {
    Function<String, Integer> fun = func(String::length);
    Assert.assertEquals(4, fun.apply("test").intValue());
  }

  @Test
  public void testFunc0() {
    Function0<String> fn0 = func0(() -> "yep");
    Assert.assertEquals("yep", fn0.apply());
  }

  @Test(expected = Exception.class)
  public void testMakeExceptionalFunctionFromLambda() {
    ExceptionalFunction<String,String> fun =
      exfunc(str -> { throw new Exception("Expected"); });
    fun.apply("test");
  }

  @Test
  public void testMakeUnitFunction() {
    Function<String, BoxedUnit> fun = cons(System.out::println);
    Assert.assertEquals(BoxedUnit.UNIT, fun.apply("test"));
  }

  @Test(expected = Exception.class)
  public void makeExceptionalUnitFunction() throws Exception {
    Function<String, BoxedUnit> fun =
      excons(value -> { throw new Exception("Expected"); });
    fun.apply("test");
  }

}
