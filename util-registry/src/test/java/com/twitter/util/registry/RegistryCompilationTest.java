package com.twitter.util.registry;

import com.twitter.util.registry.GlobalRegistry;
import com.twitter.util.registry.Registry;
import org.junit.Test;

public class RegistryCompilationTest {

  @Test
  public void testPut() {
    Registry registry = GlobalRegistry.get();
    registry.put("foo", "bar", "baz");
    registry.put("foo", "qux");
    registry.put("baz");
  }
}