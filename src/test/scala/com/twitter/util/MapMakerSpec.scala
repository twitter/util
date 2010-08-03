package com.twitter.util

import org.specs.Specification

object MapMakerSpec extends Specification {
  "MapMaker" should {
    class Item
    case class Cell[A](var elem: A)
    val id = 1010101L
    val r = Runtime.getRuntime()

    "A weak value map removes items when there is no longer a reference to them" in {
      val weakValueMap = MapMaker[Int, Item](_.weakValues)
      val cell = new Cell(new Item)
      weakValueMap += 1 -> cell.elem
      r.gc()
      weakValueMap get(1) must beSome(cell.elem)
      cell.elem = null
      r.gc()
      weakValueMap get(1) must beNone
    }
  }
}
