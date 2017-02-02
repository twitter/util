package com.twitter.util.javainterop;

import java.util.*;

import scala.Option;

import org.junit.Test;

import static com.twitter.util.javainterop.Scala.*;

import static org.junit.Assert.*;

public class ScalaTest {

  @Test
  public void testAsImmutableSeq() {
    assertEquals(0, asImmutableSeq(null).size());

    List<String> list = new ArrayList<>();
    assertEquals(0, asImmutableSeq(list).size());

    list.add("1");
    scala.collection.immutable.Seq<String> sList1 = asImmutableSeq(list);
    assertEquals(1, sList1.size());
    assertEquals("1", list.get(0));

    list.add("2");
    // make sure that didn't change the 1st list (in other words, make sure they
    // aren't sharing the underlying data structure).
    assertEquals(1, sList1.size());
    scala.collection.immutable.Seq<String> sList2 = asImmutableSeq(list);
    assertEquals(2, sList2.size());
    assertEquals("1", sList2.apply(0));
    assertEquals("2", sList2.apply(1));
  }

  @Test
  public void testAsImmutableSet() {
    assertEquals(0, asImmutableSet(null).size());

    Set<String> set = new HashSet<>();
    assertEquals(0, asImmutableSet(set).size());

    set.add("1");
    scala.collection.immutable.Set<String> sSet1 = asImmutableSet(set);
    assertEquals(1, sSet1.size());
    assertTrue(sSet1.contains("1"));

    set.add("2");
    // make sure that didn't change the 1st list (in other words, make sure they
    // aren't sharing the underlying data structure).
    assertEquals(1, sSet1.size());
    scala.collection.immutable.Set<String> sSet2 = asImmutableSet(set);
    assertEquals(2, sSet2.size());
    assertTrue(sSet2.contains("1"));
    assertTrue(sSet2.contains("2"));
  }

  @Test
  public void testAsImmutableMap() {
    assertEquals(0, asImmutableMap(null).size());

    Map<String, String> map = new HashMap<>();
    assertEquals(0, asImmutableMap(map).size());

    map.put("1", "a");
    scala.collection.immutable.Map<String, String> sMap1 = asImmutableMap(map);
    assertEquals(1, sMap1.size());
    assertEquals("a", sMap1.apply("1"));

    map.put("2", "b");
    // make sure that didn't change the 1st map (in other words, make sure they
    // aren't sharing the underlying data structure).
    assertEquals(1, sMap1.size());
    scala.collection.immutable.Map<String, String> sMap2 = asImmutableMap(map);
    assertEquals(2, sMap2.size());
    assertEquals("a", sMap2.apply("1"));
    assertEquals("b", sMap2.apply("2"));
  }

  @Test
  public void testAsOption() {
    assertTrue(asOption(Optional.empty()).isEmpty());
    assertTrue(asOption(Optional.ofNullable(null)).isEmpty());

    Option<String> strOption = asOption(Optional.of("hello"));
    assertTrue(strOption.isDefined());
    assertEquals("hello", strOption.get());

    Option<Integer> intOption = asOption(Optional.of(5));
    assertTrue(intOption.isDefined());
    assertEquals(5, (int) intOption.get());
  }

}
