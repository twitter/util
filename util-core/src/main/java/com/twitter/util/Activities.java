package com.twitter.util;

import scala.Tuple2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A Java adaptation of {@link com.twitter.util.Activity} companion object.
 */
public final class Activities {
  private Activities() { }

  /**
   * @see com.twitter.util.Activity$#apply(Var)
   */
  public static <T> Activity<T> newActivity(Var<Activity.State<T>> var) {
    return Activity$.MODULE$.apply(var);
  }

  /**
   * @see com.twitter.util.Activity$#apply()
   */
  public static <T> Tuple2<Activity<T>, Witness<Try<T>>> newActivity() {
    return Activity$.MODULE$.apply();
  }

  /**
   * @see com.twitter.util.Activity$#value(Object)
   */
  public static <T> Activity<T> newValueActivity(T value) {
    return Activity$.MODULE$.value(value);
  }

  /**
   * @see com.twitter.util.Activity$#future(Future)
   */
  public static <T> Activity<T> newFutureActivity(Future<T> future) {
    return Activity$.MODULE$.future(future);
  }

  /**
   * @see com.twitter.util.Activity$#pending()
   */
  @SuppressWarnings("unchecked")
  public static <T> Activity<T> newPendingActivity() {
    return (Activity<T>) Activity$.MODULE$.pending();
  }

  /**
   * @see com.twitter.util.Activity$#exception(Throwable)
   */
  @SuppressWarnings("unchecked")
  public static <T> Activity<T> newFailedActivity(Throwable throwable) {
    return (Activity<T>) Activity$.MODULE$.exception(throwable);
  }

  /**
   * Checks whether the given {@code state} is a value activity state.
   */
  public static boolean isValueState(Activity.State<?> state) {
    return state instanceof Activity.Ok;
  }

  /**
   * Checks whether the given {@code state} is a failed activity state.
   */
  public static boolean isFailedState(Activity.State<?> state) {
    return state instanceof Activity.Failed;
  }

  /**
   * Checks whether the given {@code state} is a pending activity state.
   */
  public static boolean isPendingState(Activity.State<?> state) {
    return state instanceof Activity.Pending$;
  }

  /**
   * @see com.twitter.util.Activity$#sample(Activity)
   */
  public static <T> T sample(Activity<T> activity) {
    return Activity$.MODULE$.sample(activity);
  }

  /**
   * @see com.twitter.util.Activity$#collect(java.util.List)
   */
  public static <T> Activity<Collection<T>> collect(Collection<Activity<T>> activities) {
    List<Activity<T>> in = new ArrayList<Activity<T>>(activities);
    Activity<List<T>> out = Activity$.MODULE$.collect(in);
    return out.map(new Function<List<T>, Collection<T>>() {
      @Override
      public Collection<T> apply(List<T> list) {
        return list;
      }
    });
  }
}
