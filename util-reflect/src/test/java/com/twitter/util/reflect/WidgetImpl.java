package com.twitter.util.reflect;

import java.io.Serializable;
import java.lang.annotation.Annotation;

class WidgetImpl implements Widget, Serializable {
  private final String value;

  public WidgetImpl(String value) {
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
   * Widget specific equals
   */
  public boolean equals(Object o) {
    if (!(o instanceof Widget)) {
      return false;
    }

    Widget other = (Widget) o;
    return value.equals(other.value());
  }

  public String toString() {
    return "@" + Widget.class.getName() + "(value=" + value + ")";
  }

  public Class<? extends Annotation> annotationType() {
    return Widget.class;
  }

  private static final long serialVersionUID = 0;
}
