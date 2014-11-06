package com.twitter.util;

/**
 * A Java adaptation of {@link com.twitter.util.Event} companion object.
 */
public final class Events {
  private Events() { }

  /**
   * @see com.twitter.util.Event$#apply()
   */
  public static <T> WitnessedEvent<T> newEvent() {
    return new WitnessedEvent<T>();
  }
}
