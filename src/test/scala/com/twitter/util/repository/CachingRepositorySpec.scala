package com.twitter.util.repository

import org.specs.Specification
import org.specs.mock.Mockito
import scala.collection.mutable
import com.twitter.util.Future

object CachingRepositorySpec extends Specification with Mockito {
  class Query
  class Item

  val backingRepository: Repository[Query, Item] = mock[Repository[Query, Item]]
  val map = new mutable.HashMap[Long, Future[Throwable, Option[Item]]]
  val index = new mutable.HashMap[Query, Future[Throwable, Seq[Item]]]
  val repository = new CachingRepository[Query, Item](map, index, backingRepository)
  val item = mock[Item]
  val id = 101010L
  val query = mock[Query]

  "CachingRepository" should {
    "finding by primary key" >> {
      "when the cache map has the item, it returns Some(item) without calling the backing repository" in {
        map += id -> Future(Some(item))
        repository(id)() mustEqual Some(item)
        //backingRepository(id) was notCalled
        there was no(backingRepository)(id)
      }

      "when the cache map does not have the item, it calls into the backing repository" in {
        "when the backing repository has the item, it returns Some(item) and caches the result" in {
          backingRepository(id) returns Future(Some(item))
          repository(id)() mustEqual Some(item)
          there was one(backingRepository)(id) //was called.once
          map(id)() mustEqual Some(item)
        }

        "when the backing repository does not have the item, it returns None and caches the result" in {
          backingRepository(id) returns Future(None)
          repository(id)() mustEqual None
          there was one(backingRepository)(id)// was called.once
          map(id)() mustEqual None
        }
      }
    }

    "finding by a query / secondary index" >> {
      "when the cache is empty, it calls the backing repository and populates the cache" in {
        backingRepository(query) returns Future(Seq(item))
        repository(query)().toList mustEqual Seq(item).toList
        repository(query)().toList mustEqual Seq(item).toList
        there was one(backingRepository)(query)// was called.once
      }
    }
  }
}
