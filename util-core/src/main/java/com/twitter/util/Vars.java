package com.twitter.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A Java adaptation of {@link com.twitter.util.Var} companion object.
 */
public final class Vars {
  private Vars() { }

  /**
   * @see com.twitter.util.Var$#sample(Var)
   */
  public static <T> T sample(Var<T> var) {
    return Var$.MODULE$.sample(var);
  }

  /**
   * @see com.twitter.util.Var$#apply(Object)
   */
  public static <T> ReadWriteVar<T> newVar(T init) {
    return new ReadWriteVar<T>(init);
  }

  /**
   * @see com.twitter.util.Var$#apply(Object, Event)
   */
  public static <T> Var<T> newVar(T init, Event<T> event) {
    return Var$.MODULE$.apply(init, event);
  }

  /**
   * @see com.twitter.util.Var$#value(Object)
   */
  public static <T> ConstVar<T> newConstVar(T constant) {
    return new ConstVar<T>(constant);
  }

  /**
   * @see com.twitter.util.Var$#collect(java.util.List)
   */
  public static <T> Var<Collection<T>> collect(Collection<Var<T>> vars) {
    List<Var<T>> in = new ArrayList<Var<T>>(vars);
    Var<List<T>> out = Var$.MODULE$.collect(in);
    return out.map(new Function<List<T>, Collection<T>>() {
      @Override
      public Collection<T> apply(List<T> list) {
        return list;
      }
    });
  }

  /**
   * @see com.twitter.util.Var$#async(Object, scala.Function1)
   */
  public static <T> Var<T> async(T empty, Function<Updatable<T>, Closable> function) {
    return Var$.MODULE$.async(empty, function);
  }
}
