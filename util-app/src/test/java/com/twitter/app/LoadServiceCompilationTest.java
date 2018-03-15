package com.twitter.app;

import java.util.Collections;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class LoadServiceCompilationTest {

  private interface Iface { }

  private static class Impl implements Iface { }

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

}
