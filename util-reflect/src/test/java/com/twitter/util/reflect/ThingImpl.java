package com.twitter.util.reflect;

import java.io.Serializable;
import java.lang.annotation.Annotation;

class ThingImpl implements Thing, Serializable {
  private final String value;

  public ThingImpl(String value) {
    this.value = value;
  }

  public String value() {
    return this.value;
  }

  public int hashCode() {
    // This is specified in java.lang.Annotation.
    return (127 * "value".hashCode()) ^ value.hashCode();
  }

  /**
   * Thing specific equals
   */
  public boolean equals(Object o) {
    if (!(o instanceof Thing)) {
      return false;
    }

    Thing other = (Thing) o;
    return value.equals(other.value());
  }

  public String toString() {
    return "@" + Thing.class.getName() + "(value=" + value + ")";
  }

  public Class<? extends Annotation> annotationType() {
    return Thing.class;
  }

  private static final long serialVersionUID = 0;
}
