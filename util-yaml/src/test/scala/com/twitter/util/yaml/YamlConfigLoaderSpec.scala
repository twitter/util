package com.twitter.util.yaml

import java.util.Collections
import org.specs.Specification
import org.specs.mock._

object YamlConfigLoaderSpec extends Specification with Mockito {
  object ListYamlConfigLoader extends YamlConfigLoader[java.util.List[String]](
    Collections.EMPTY_LIST.asInstanceOf[java.util.List[String]]
  )

  "YamlConfigLoader" should {
    doBefore {
      YamlConfigLoader.defaultConfigRoot = ""
    }

    "load from resource directory" in {
      ListYamlConfigLoader.loadYaml("test.yml").toString must_== "[resource]"
    }

    "handle empty yaml" in {
      ListYamlConfigLoader.loadYaml("").toString must_== "[]"
    }

    "handle empty file" in {
      var defaultConfigRoot = ""
      val file = getClass.getResource("/empty.yml").getPath
      ListYamlConfigLoader.loadYaml(file).toString must_== "[]"
    }

    // "load from custom directory" in {
    //   YamlConfigLoader.defaultConfigRoot = "config/"
    //   ListYamlConfigLoader.loadYaml("test.yml").toString must_== "[config]"
    // }

    "load yaml" in {
      ListYamlConfigLoader.loadYaml("- yaml").toString must_== "[yaml]"
    }

    "throw when file not found" in {
      ListYamlConfigLoader.loadYaml("unknowfile.yml") must throwA[IllegalArgumentException]
    }

    "throw when given bad yaml" in {
      ListYamlConfigLoader.loadYaml("not valid yaml") must throwA[ClassCastException]
    }
  }
}
