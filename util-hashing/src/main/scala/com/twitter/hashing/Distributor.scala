package com.twitter.hashing

trait Distributor[A] {
  def nodeForHash(Hash: Long): A
  def nodeCount: Int
  def nodes: Seq[A]
}