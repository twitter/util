package com.twitter.hashing

trait Distributor[A] {
  def nodeForKey(key: String): A
  def nodeCount: Int
}