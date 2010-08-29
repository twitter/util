package com.twitter.util.repository

import com.twitter.util.Future

trait Repository[Q, E] {
  def apply(id: Long): Future[Throwable, Option[E]]
  def apply(query: Q): Future[Throwable, Seq[E]]
}