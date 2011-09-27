package com.twitter.util.yaml

import org.specs.Specification
import org.specs.mock._

object ListYamlConfigSpec extends Specification with Mockito {
  "ListYamlConfig" should {
    val Yaml = """
    - elem0
    - elem1
    - elem2
    - elem3
    """
    "Parse list yaml" in {
      val config = new ListYamlConfig(Yaml)
      (0 to 3) foreach { i =>
        val exp = "elem" + i
        config(i) must_== exp
        config.contains(exp) must beTrue
      }
    }
  }
}
