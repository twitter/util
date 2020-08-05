package com.twitter.util.lint

import org.scalatest.funsuite.AnyFunSuite

class GlobalRulesTest extends AnyFunSuite {
  private val neverRule = Rule.apply(Category.Performance, "R2", "Good") {
    Nil
  }

  private val alwaysRule = Rule.apply(Category.Performance, "R3", "Nope") {
    Seq(Issue("lol"))
  }

  private val rules = new RulesImpl()
  rules.add(neverRule)
  rules.add(alwaysRule)

  def mkRules(): Rules = GlobalRules.withRules(rules) {
    GlobalRules.get
  }

  test("GlobalRules can write, swap and then read the old write") {
    val naive = new RulesImpl()

    GlobalRules.get.add(neverRule)
    GlobalRules.withRules(naive) {
      // neverRules should not be present
      assert(GlobalRules.get.iterable.isEmpty)
      GlobalRules.get.add(alwaysRule)
    }

    // alwaysRule should not be present
    assert(GlobalRules.get.iterable.size == 1)
    // should just be neverRule
    assert(GlobalRules.get.iterable.seq.head == neverRule)
  }

}
