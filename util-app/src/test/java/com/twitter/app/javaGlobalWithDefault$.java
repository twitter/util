package com.twitter.app;

public final class javaGlobalWithDefault$ extends JavaGlobalFlag<String> {
  private javaGlobalWithDefault$() {
    super("default value", "help string here", Flaggable.ofString());
  }

  public static final Flag<String> Flag = new javaGlobalWithDefault$();

  public static Flag<?> globalFlagInstance() {
    return Flag;
  }
}
