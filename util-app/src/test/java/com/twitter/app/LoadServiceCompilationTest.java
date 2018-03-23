package com.twitter.app;

import java.util.Collections;

import scala.collection.Seq;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LoadServiceCompilationTest {

  private interface Iface { }

  private static class Impl implements Iface { }

  private interface Apply { }

  @Test
  public void testBindingSingleImplementation() {
    LoadService.Binding<Iface> binding =
      new LoadService.Binding<>(Iface.class, new Impl());
    assertNotNull(binding);
  }

  @Test
  public void testBindingMultipleImplementation() {
    LoadService.Binding<Iface> binding =
      new LoadService.Binding<>(Iface.class, Collections.singletonList(new Impl()));
    assertNotNull(binding);
  }

  @Test
  public void apply() {
    Seq<Apply> applys = LoadService.apply(Apply.class);
    assertEquals(0, applys.size());
  }

}
