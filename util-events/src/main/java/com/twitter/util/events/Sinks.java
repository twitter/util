package com.twitter.util.events;

/**
 * Java friendly API for {@link com.twitter.util.events.Sink}.
 */
public final class Sinks {
  private Sinks() { }

  public static final Sink DEFAULT_SINK = Sink$.MODULE$.getDefault();
}
