# util-cache

`util-cache` is a library for caching asynchronous values in-memory.

## Motivation

There are three things which make caching asynchronous values different from normal ones.

1. We must evict failures properly.
2. We should not start work to produce an asynchronous value more
than once.
3. We should not cancel work for other workloads when one workload is interrupted.

The first is pretty easy, you simply need to set up a handler so that when the future fails, it's
evicted.  The second is also pretty easy–it simply requires that you cache the actual future, not
the result.  it can be done in other ways, but this is the simplest.  The third is a little tricky.
It uses Twitter Future’s "detachable" Promises, which can be interrupted efficiently without
cancelling the underlying work, but can still cancel the work that would have been done on that
future.  As an example:

The first thread comes in, and tries to read a key, "FANCY_KEY" from the cache.  It sees that it
isn't cached, so it populates the cache with the Future.  We add a handler to the Future, so that
when it's returned, it logs the returned message.

The second thread comes in, and tries to read the same key, and gets a handle on a Future (the same
one, so we don't duplicate work).

The first thread passes some timeout, and cancels the work.  We want it to tear down the handler
that it had registered on the Future, but we don't want it to cancel the underlying work–the second
thread will need it, after all.

## Key-value Caches

### Core Idea

The core idea is that we combine a few simple primitives, and it gives us everything that we need.
Those primitives are:

A. [AsyncMemoize][0] (caching)
B. [EvictingCache][1] (eviction)
C.  [interruption][2] (this is simple with Twitter Futures so it doesn't need its own class)


We strongly encourage users to use [Guava caches][3], as the backing synchronous cache.  Once you’ve
constructed your cache, you can hand it to [GuavaCache][4], which will construct it for you
correctly.

### Quickstart

To get started, all we need is a function that returns a future, and a cache.

```scala
import com.google.common.cache.{CacheBuilder, Cache}
import com.twitter.util.Future
import java.util.concurrent.TimeUnit

val fn: Req => Future[Rep] = ???
val cache: Cache[Req, Future[Rep]] = CacheBuilder.newBuilder[Req, Future[Rep]]()
  .maximumSize(10000)
  .expireAfterWrite(10, TimeUnit.MINUTES)
  .build()
val cachedFn: Req => Future[Rep] = GuavaCache.fromCache(fn, cache)
```

Some users may want to use Guava’s `LoadingCache`, which works equally well.

```scala
import com.google.common.cache.{CacheBuilder, LoadingCache, CacheLoader}
import com.twitter.util.Future
import java.util.concurrent.TimeUnit

val fn: Req => Future[Rep] = ???
val cache: LoadingCache[Req, Future[Rep]] = CacheBuilder.newBuilder[Req, Future[Rep]]()
  .maximumSize(10000)
  .expireAfterWrite(10, TimeUnit.MINUTES)
  .build(new CacheLoader[Req, Future[Rep]]() {
    def load(Req req): Future[Rep] = {
      fn(req)
    }
  })
val cachedFn: Req => Future[Rep] = GuavaCache.fromLoadingCache(cache)
```

### Advanced Usage

Although the default tools will be appropriate most of the time, they may not be right for all use
cases.  You may want to use the `util-cache` primitives to assemble a cache with a custom contract.
For example, we assume that users will not want to cancel work for all workloads for a shared key if
only one is cancelled, but this may not be the case. It could be that a specific key should not be
fetched, for example if the key is deleted, and a client is trying to purge it from caches.  Another
possible scenario is if we know that races are unlikely, and it will be useful to avoid doing the
underlying work because it’s expensive.

In this case, we might start with something like:

```scala
import com.google.common.cache.{CacheBuilder, Cache}
import com.twitter.cache.FutureCache
import com.twitter.util.Future

val fn: K => Future[V] = ??? // we assume this is provided
val gCache: Cache[K, Future[Rep]] = CacheBuilder.newBuilder[Req, Future[Rep]]()
  .maximumSize(10000)
  .expireAfterWrite(10, TimeUnit.MINUTES)
  .build()
// we don’t use GuavaCache.default so we can add defaults manually
val cache: FutureCache[Req, Rep] = new GuavaCache(gCache)
val shared = FutureCache.default(fn, cache)
```

which under the hood is doing:

```scala
import com.twitter.cache.{AsyncMemoize, EvictingCache, FutureCache}
import com.twitter.util.Future
import java.util.concurrent.TimeUnit

val fn: K => Future[V] = ??? // we assume this is provided
val cache: FutureCache = ???  // we assume this is provided, probably using GuavaCache
val shared = AsyncMemoize(fn, new EvictingCache(cache)).andThen { f: Future[V] => f.interruptible() }
```

so we could just as easily instead use:

```scala
import com.twitter.cache.{AsyncMemoize, EvictingCache, FutureCache}
import com.twitter.util.Future

val fn: K => Future[V] = ??? // we assume this is provided
val cache: FutureCache = ???  // we assume this is provided, probably using GuavaCache
val owned = AsyncMemoize(fn, new EvictingCache(cache))
```

Which now gives us the behavior we were looking for.  When we interrupt a future returned by
`owned`, it will discard the underlying work and fail the workloads for that key for other threads
too.

## Single-value Caches

We provide a simple interface for cases where a service must cache a single piece of frequently
accessed data, and only needs to refresh it periodically.  For example, if a client needs to know
some metadata about a server, but it's OK to have metadata which is stale for up to one hour, we
could use the single-value cache.

If a request fails, it is refreshed lazily, so that the next time a user asks for the result, it
forces the refresh.

### Quickstart

Suppose we want to read a file which is managed by puppet into memory every five minutes.

```scala
import com.twitter.cache.Refresh
import com.twitter.util.{Future, FuturePool}
import scala.io.Source

def readFile(): Future[String] = FuturePool.unboundedPool {
  Source.fromFile("/etc/puppeted_file").mkString
}
val cachedFile: () => Future[String] = Refresh.every(5.minutes) {
  readFile()
}

```

[0]: https://github.com/twitter/util/blob/develop/util-cache/src/main/scala/com/twitter/cache/AsyncMemoize.scala
[1]: https://github.com/twitter/util/blob/develop/util-cache/src/main/scala/com/twitter/cache/EvictingCache.scala
[2]: https://github.com/twitter/util/blob/f5e363e5dbfa42a49478e0324099a7f2884cf6d8/util-cache/src/main/scala/com/twitter/cache/FutureCache.scala#L101-L109
[3]: https://github.com/google/guava/wiki/CachesExplained
[4]: https://github.com/twitter/util/blob/develop/util-cache/src/main/scala/com/twitter/cache/guava/GuavaCache.scala
