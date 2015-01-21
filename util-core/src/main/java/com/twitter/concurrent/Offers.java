package com.twitter.concurrent;

import com.twitter.util.Duration;
import com.twitter.util.Function0;
import com.twitter.util.Future;
import com.twitter.util.Timer;
import scala.collection.JavaConversions;
import scala.runtime.BoxedUnit;

import java.util.ArrayList;
import java.util.Collection;

/**
 *  A Java adaptation of {@link com.twitter.concurrent.Offer} companion object.
 */
public final class Offers {
  private Offers() { }

  /**
   * @see Offer$#never()
   */
  public static final Offer<?> NEVER = Offer$.MODULE$.never();

  /**
   * @see Offer$#const(scala.Function0)
   *
   * Note: Updates here must also be done at {@link com.twitter.concurrent.Offer$#const(scala.Function0)}
   */
  public static <T> Offer<T> newConstOffer(final Function0<T> function) {
    return new AbstractOffer<T>() {
      @Override
      public Future<Tx<T>> prepare() {
        return Future.value(Txs.newConstTx(function.apply()));
      }
    };
  }

  /**
   * Creates a new {@link Offer} from given constant {@code value}.
   *
   * Note: Updates here must also be done at {@link com.twitter.concurrent.Offer$#const(scala.Function0)}
   */
  public static <T> Offer<T> newConstOffer(final T value) {
    return Offers.newConstOffer(new Function0<T>() {
      @Override
      public T apply() {
        return value;
      }
    });
  }

  /**
   * @see Offer$#timeout(com.twitter.util.Duration, com.twitter.util.Timer)
   */
  public static Offer<BoxedUnit> newTimeoutOffer(Duration duration, Timer timer) {
    return Offer$.MODULE$.timeout(duration, timer);
  }

  /**
   * @see Offer$#choose(scala.collection.Seq)
   */
  public static <T> Offer<T> choose(Collection<Offer<T>> offers) {
    return Offer$.MODULE$.choose(JavaConversions.asScalaBuffer(new ArrayList<Offer<T>>(offers)));
  }

  /**
   * @see Offer$#select(scala.collection.Seq)
   */
  public static <T> Future<T> select(Collection<Offer<T>> offers) {
    return Offers.choose(offers).sync();
  }
}
