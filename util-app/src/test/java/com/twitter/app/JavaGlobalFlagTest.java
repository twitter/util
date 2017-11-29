package com.twitter.app;

import java.util.concurrent.atomic.AtomicInteger;

import scala.Option;
import scala.collection.Seq;
import scala.runtime.BoxedUnit;

import org.junit.Test;

import com.twitter.util.Function0;

import static com.twitter.util.Function.func;
import static com.twitter.util.Function.func0;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JavaGlobalFlagTest {

  @Test
  public void testGet() {
    Option<Flag<?>> flagOption =
        GlobalFlag$.MODULE$.get("com.twitter.app.javaGlobalWithDefault");
    assertTrue(flagOption.isDefined());
    Flag<?> flag = flagOption.get();
    assertEquals("default value", flag.apply());
  }

  @Test
  public void testLet() {
    Flag<String> flag = javaGlobalDollar$.Flag;
    assertEquals("returned", flag.let("ok", func0(() -> "returned")));
  }

  @Test
  public void testFlagWithDefault() {
    assertFalse(javaGlobalWithDefault$.Flag.isDefined());
    assertEquals("default value", javaGlobalWithDefault$.Flag.apply());

    final AtomicInteger i = new AtomicInteger(0);
    Function0<BoxedUnit> fn0 = new Function0<BoxedUnit>() {
      @Override
      public BoxedUnit apply() {
        i.incrementAndGet(); // used to verify this block is executed.
        assertTrue(javaGlobalWithDefault$.Flag.isDefined());
        assertEquals("let value", javaGlobalWithDefault$.Flag.apply());
        return BoxedUnit.UNIT;
      }
    };
    javaGlobalWithDefault$.Flag.let("let value", fn0);
    assertEquals(1, i.get());
  }

  @Test
  public void testFlagWithoutDefault() {
    assertFalse(javaGlobalNoDefault$.Flag.isDefined());
    assertTrue(javaGlobalNoDefault$.Flag.get().isEmpty());

    final AtomicInteger i = new AtomicInteger(0);
    Function0<BoxedUnit> fn0 = new Function0<BoxedUnit>() {
      @Override
      public BoxedUnit apply() {
        i.incrementAndGet(); // used to verify this block is executed.
        assertTrue(javaGlobalNoDefault$.Flag.isDefined());
        assertEquals(Integer.valueOf(5), javaGlobalNoDefault$.Flag.apply());
        return BoxedUnit.UNIT;
      }
    };
    javaGlobalNoDefault$.Flag.let(5, fn0);
    assertEquals(1, i.get());
  }

  @Test
  public void testIncludedInGetAll() {
    Seq<Flag<?>> flags = GlobalFlag$.MODULE$.getAll(getClass().getClassLoader());
    assertTrue(
      "Flags found: " + flags.toString(),
      flags.exists(func(flag ->
        flag.name().equals("com.twitter.app.javaGlobalWithDefault"))));
  }
}
