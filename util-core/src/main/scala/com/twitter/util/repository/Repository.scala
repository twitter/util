package com.twitter.util.repository

import com.twitter.util.Future

trait Repository[Q, E] {
  def apply(id: Long): Future[Option[E]]
  def apply(query: Q): Future[Seq[E]]
}
