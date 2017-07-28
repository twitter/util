package com.twitter.util.logging

@SerialVersionUID(1L)
class TestSerializable(val left: Int, val right: Int) extends Serializable with Logging {

  def total = {
    info(s"Adding $left + $right")
    val t = left + right
    info(s"Total = $t")
    t
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case that: TestSerializable =>
      this.left == that.left && this.right == that.right && logger != null
    case _ => false
  }

  override def hashCode(): Int = {
    left.hashCode() + right.hashCode() + logger.hashCode()
  }

  override def toString = s"$left + $right = $total"
}
