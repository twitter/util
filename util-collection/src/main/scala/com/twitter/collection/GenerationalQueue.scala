package com.twitter.collection

import collection._
import com.twitter.util.{Duration, Time}
import com.twitter.util.TimeConversions._

trait GenerationalQueue[A] {
  def touch(a: A)
  def add(a: A)
  def remove(a: A)
  def collect(d: Duration): Option[A]
  def collectAll(d: Duration): Iterable[A]
}

/**
 * Generational Queue keep track of elements based on their last activity.
 * You can refresh activity of an element by calling touch(a: A) on it.
 * There is 2 ways of retrieving old elements:
 * - collect(age: Duration) collect the oldest element (age of elem must be > age)
 * - collectAll(age: Duration) collect all the elements which age > age in parameter
 */
class ExactGenerationalQueue[A] extends GenerationalQueue[A] {
  private[this] val container = mutable.HashMap.empty[A, Time]
  private[this] implicit val ordering = Ordering.by[(A, Time), Time]{ case (_, ts) => ts }

  /**
   * touch insert the element if it is not yet present
   */
  def touch(a: A) = synchronized { container.update(a, Time.now) }

  def add(a: A) = synchronized { container += ((a, Time.now)) }

  def remove(a: A) = synchronized { container.remove(a) }

  def collect(age: Duration): Option[A] = synchronized {
    if (container.isEmpty)
      None
    else
      container.min match {
        case (a, t) if (t.untilNow > age) => Some(a)
        case _ => None
      }
  }

  def collectAll(age: Duration): Iterable[A] = synchronized {
    (container filter { case (_, t) => t.untilNow > age }).keys
  }
}


/**
 * Improved GenerationalQueue: using a list of buckets responsible for containing elements belonging
 * to a slice of time.
 * For instance: 3 Buckets, First contains elements from 0 to 10, second elements from 11 to 20
 * and third elements from 21 to 30
 * We expand the list when we need a new bucket, and compact the list to stash all old buckets
 * into one.
 * There is a slightly difference with the other implementation, when we collect elements we only
 * choose randomly an element in the oldest bucket, as we don't have activity date in this bucket
 * we consider the worst case and then we can miss some expired elements by never find elements
 * that aren't expired.
 */
class BucketGenerationalQueue[A](timeout: Duration) extends GenerationalQueue[A]
{
  object TimeBucket {
    def empty[A] = new TimeBucket[A](Time.now, timeSlice)
  }
  class TimeBucket[A](var origin: Time, var span: Duration) extends mutable.HashSet[A] {

    // return the age of the potential youngest element of the bucket (may be negative if the
    // bucket is not yet expired)
    def age(now: Time = Time.now): Duration = (origin + span).until(now)

    def ++=(other: TimeBucket[A]) = {
      origin = List(origin, other.origin).min
      span = List(origin + span, other.origin + other.span).max - origin

      super.++=(other)
      this
    }

    override def toString() = "TimeBucket(origin=%d, size=%d, age=%s, count=%d)".format(
      origin.inMilliseconds, span.inMilliseconds, age().toString, super.size
    )
  }

  private[this] val timeSlice = timeout / 3
  private[this] var buckets = List[TimeBucket[A]]()

  private[this] def maybeGrowChain() = {
    // NB: age of youngest element is negative when bucket isn't expired
    val growChain = buckets.headOption.map((bucket) => {
      bucket.age() > Duration.Zero
    }).getOrElse(true)

    if (growChain)
      buckets = TimeBucket.empty[A] :: buckets
    growChain
  }

  private[this] def compactChain(): List[TimeBucket[A]] = {
    val now = Time.now
    // partition equivalent to takeWhile/dropWhile because buckets are ordered
    val (news, olds) = buckets.partition(_.age(now) < timeout)
    if (olds.isEmpty)
      news
    else {
      val tailBucket = olds.head
      olds drop 1 foreach { tailBucket ++= _ }
      if (tailBucket.isEmpty)
        news
      else
        news ::: List(tailBucket)
    }
  }

  private[this] def updateBuckets() {
    if (maybeGrowChain())
      buckets = compactChain()
  }

  def touch(a: A) = synchronized {
    buckets drop 1 foreach { _.remove(a) }
    add(a)
  }

  def add(a: A) = synchronized {
    updateBuckets()
    buckets.head.add(a)
  }

  def remove(a: A) = synchronized {
    buckets foreach { _.remove(a) }
    buckets = compactChain()
  }

  def collect(d: Duration): Option[A] = synchronized {
    if (buckets.isEmpty)
      return None

    if (buckets.last.isEmpty)
      buckets = compactChain()

    val oldestBucket = buckets.last
    if (d < oldestBucket.age())
      oldestBucket.headOption
    else
      None
  }

  def collectAll(d: Duration): Iterable[A] = synchronized {
    (buckets dropWhile(_.age() < d)).flatten
  }
}
