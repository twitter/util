package com.twitter.hashing

trait Distributor[A] {
  def entryForHash(hash: Long): (Long, A)
  def partitionIdForHash(hash: Long): Long
  def nodeForHash(hash: Long): A
  def nodeCount: Int
  def nodes: Seq[A]
}

class SingletonDistributor[A](node: A) extends Distributor[A] {
  def entryForHash(hash: Long): (Long, A) = (hash, node)
  def partitionIdForHash(hash: Long): Long = hash
  def nodeForHash(hash: Long): A = node
  def nodeCount = 1
  def nodes: Seq[A] = Seq(node)
}
