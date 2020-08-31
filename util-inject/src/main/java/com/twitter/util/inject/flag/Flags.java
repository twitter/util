package com.twitter.util.inject.flag;

import com.google.inject.Key;

/**
 * Utility methods for use with {@code @}{@link Flagged}. Pattern copied from
 * {@code com.google.inject.name.Names}.
 *
 * @see <a href="https://github.com/google/guice/blob/master/core/src/com/google/inject/name/Names.java"></a>
 * @see Flagged
 */
public final class Flags {
  private Flags() {
  }

  /** Creates a {@link Flagged} annotation with {@code name} as the value. */
  public static Flagged named(String name) {
    return new FlaggedImpl(name);
  }

  /**
   * Builds a {@code com.google.inject.Key} over a String type annotated
   * with {@code @}{@link Flagged}(named)
   *
   * @see <a href="https://github.com/google/guice/blob/master/core/src/com/google/inject/Key.java"></a>
   */
  public static Key<String> key(String name) {
    return Key.get(String.class, Flags.named(name));
  }
}
