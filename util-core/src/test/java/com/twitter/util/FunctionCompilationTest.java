package com.twitter.util;

import org.junit.Test;
import scala.runtime.BoxedUnit;

import static com.twitter.util.Function.c;
import static com.twitter.util.Function.f;
import static com.twitter.util.Function.xc;
import static com.twitter.util.Function.xf;

/**
 * Tests are not currently run for java, but for our purposes, if the test compiles at all, it's
 * a success.
 */
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
    Function<String,String> fun = f(new JavaFunction<String, String>() {
      @Override
      public String apply(String value) {
        return value.toUpperCase();
      }
    });

    // This syntax works in Java 8:
    // fun = f(String::toUpperCase);

    fun.apply("test");
  }

  @Test(expected = Exception.class)
  public void testMakeExceptionalFunctionFromLambda() {
    ExceptionalFunction<String,String> fun = xf(new ExceptionalJavaFunction<String, String>() {
      @Override
      public String apply(String value) throws Throwable {
        throw new Exception("Expected");
      }
    });

    // This syntax works in Java 8:
    // myFun = xf(str -> { throw new Exception("Expected"); });

    fun.apply("test");
  }

  @Test
  public void testMakeUnitFunction() {
    Function<String, BoxedUnit> fun = c(new JavaConsumer<String>() {
      @Override
      public void apply(String value) {
        System.out.println(value);
      }
    });

    // This syntax works in Java 8:
    // fun = c(System.out::println);

    fun.apply("test");
  }

  @Test(expected = Exception.class)
  public void makeExceptionalUnitFunction() throws Exception {
    Function<String, BoxedUnit> fun = xc(new ExceptionalJavaConsumer<String>() {
      @Override
      public void apply(String value) throws Exception {
        throw new Exception("Expected");
      }
    });

    // This syntax works in Java 8:
    // fun = xc(value -> { throw new Exception("Expected"); });

    fun.apply("test");
  }
}
