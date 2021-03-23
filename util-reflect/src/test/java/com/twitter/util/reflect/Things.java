package com.twitter.util.reflect;

public final class Things {
  private Things() {
  }

  /** Creates a {@link Thing} annotation with {@code name} as the value. */
  public static Thing named(String name) {
    return new ThingImpl(name);
  }
}
