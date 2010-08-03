package com.twitter.util.repository

import org.specs.Specification
import org.specs.mock.Mockito
import scala.collection.mutable
import com.twitter.util.Future

object CachingRepositorySpec extends Specification with Mockito {
  "CachingRepository" should {
    class Query
    class Item
    val map = new mutable.HashMap[Long, Future[Option[Item]]]
    val index = new mutable.HashMap[Query, Future[Seq[Item]]]
    val backingRepository = mock[Repository[Query, Item]]
    val repository = new CachingRepository[Query, Item](map, index, backingRepository)
    val item = mock[Item]
    val id = 101010L
    val query = mock[Query]

    "finding by primary key" >> {
      "when the cache map has the item, it returns Some(item) without calling the backing repository" in {
        map += id -> Future.constant(Some(item))
        repository(id)() mustEqual Some(item)
        backingRepository(id) was notCalled
      }

      "when the cache map does not have the item, it calls into the backing repository" in {
        "when the backing repository has the item, it returns Some(item) and caches the result" in {
          backingRepository(id) returns Future.constant(Some(item))
          repository(id)() mustEqual Some(item)
          backingRepository(id) was called.once
          map(id)() mustEqual Some(item)
        }

        "when the backing repository does not have the item, it returns None and caches the result" in {
          backingRepository(id) returns Future.constant(None)
          repository(id)() mustEqual None
          backingRepository(id) was called.once
          map(id)() mustEqual None
        }
      }
    }

    "finding by a query / secondary index" >> {
      "when the cache is empty, it calls the backing repository and populates the cache" in {
        backingRepository(query) returns Future.constant(Seq(item))
        repository(query)().toList mustEqual Seq(item).toList
        repository(query)().toList mustEqual Seq(item).toList
        backingRepository(query) was called.once
      }
    }
  }
}
