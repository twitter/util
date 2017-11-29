package com.twitter.app;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.twitter.util.Duration;
import com.twitter.util.Function;
import com.twitter.util.StorageUnit;
import com.twitter.util.Time;

import static com.twitter.util.Function.func0;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class JavaFlagTest {

  /**
   * A class representing a named thing. Used to illustrate flag-construction for
   * classes defined in application code. See [[com.twitter.app.Flaggable]] for
   * details.
   */
  static class Named {
    String name;

    static Flaggable<Named> ofNamed =
      Flaggable.mandatory(new Function<String, Named>() {
        @Override public Named apply(String n) {
          return new Named(n);
        }
      });

    Named(String name) {
      this.name = name;
    }
  }

  @Test
  public void testJavaFlags() {

    String applicationName = "com.twitter.app.JavaFlagTest";
    Flags flag = new Flags(applicationName);

    Flag<Named> named = flag.create("named", new Named(""), "", Named.ofNamed);
    Flag<String> stringFlag = flag.create("string", "default", "", Flaggable.ofString());
    Flag<Boolean> booleanFlag = flag.create("bool", true, "", Flaggable.ofJavaBoolean());
    Flag<Integer> integerFlag = flag.create("int", 1, "", Flaggable.ofJavaInteger());
    Flag<Long> longFlag = flag.create("long", 1L, "", Flaggable.ofJavaLong());
    Flag<Float> floatFlag = flag.create("float", 1.0f, "", Flaggable.ofJavaFloat());
    Flag<Double> doubleFlag = flag.create("double", 1.0d, "", Flaggable.ofJavaDouble());
    Flag<Duration> durationFlag =
        flag.create("duration", Duration.fromSeconds(1), "", Flaggable.ofDuration());
    Flag<StorageUnit> StorageUnitFlag =
        flag.create("storage unit", StorageUnit.zero(), "", Flaggable.ofStorageUnit());
    Flag<Time> timeFlag = flag.create("time", Time.Bottom(), "", Flaggable.ofTime());
    Flag<InetSocketAddress> addrFlag =
        flag.create("addr", new InetSocketAddress(0), "", Flaggable.ofInetSocketAddress());

    Flag<List<Integer>> listFlag = flag.create(
        "list",
        new ArrayList<>(),
        "",
        Flaggable.ofJavaList(Flaggable.ofJavaInteger())
    );

    Flag<Map<Integer, String>> mapFlag = flag.create(
        "map",
        new HashMap<>(),
        "",
        Flaggable.ofJavaMap(Flaggable.ofJavaInteger(), Flaggable.ofString())
    );

    // non-default flags test
    Flag<Integer> nonDefaultIntFlag = flag.createMandatory("mandatory-int", "you better supply this", "Integer", Flaggable.ofJavaInteger());
    Flag<String> nonDefaultStringFlag = flag.createMandatory("mandatory-str", "you better supply this", "String", Flaggable.ofString());
  }

  @Test
  public void testJavaLongFlags() {
    Flags flags = new Flags("ApplicationName");
    Flag<Long> longFlag = flags.create("long", 1234567890L, "", Flaggable.ofJavaLong());

    longFlag.parse();
    assertEquals(1234567890L, longFlag.apply().longValue());

    longFlag.parse("9876543210");
    assertEquals(9876543210L, longFlag.apply().longValue());

    try {
      longFlag.parse("9876543210L");
      fail();
    } catch (NumberFormatException expected) {
      assertEquals("For input string: \"9876543210L\"", expected.getMessage());
    }
  }

  @Test
  public void testLet() {
    Flags flags = new Flags("ApplicationName");
    Flag<Long> flag = flags.create("long", 1234567890L, "", Flaggable.ofJavaLong());
    assertEquals("ok", flag.let(5L, func0(() -> "ok")));
  }

}
