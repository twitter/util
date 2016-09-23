package com.twitter.app;

public final class javaGlobalNoDefault$ extends JavaGlobalFlag<Integer> {
  private javaGlobalNoDefault$() {
    super("help string here", Flaggable.ofJavaInteger(), Integer.class);
  }

  public static final Flag<Integer> Flag = new javaGlobalNoDefault$();

  public static Flag<?> globalFlagInstance() {
    return Flag;
  }
}
