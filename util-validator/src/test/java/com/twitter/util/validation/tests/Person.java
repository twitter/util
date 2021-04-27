package com.twitter.util.validation.tests;

import jakarta.validation.constraints.NotNull;

public class Person {
  @NotNull
  private String name;

  public Person(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
