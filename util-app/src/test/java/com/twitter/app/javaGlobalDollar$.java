package com.twitter.app;

public final class javaGlobalDollar$ extends JavaGlobalFlag<String> {
  private javaGlobalDollar$() {
    super("cash money", "dollar dollar bills", Flaggable.ofString());
  }

  public static final Flag<String> Flag = new javaGlobalDollar$();

  public static Flag<?> globalFlagInstance() {
    return Flag;
  }
}
