scala_library(
    sources = ["**/*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    dependencies = [
        "3rdparty/jvm/com/github/ben-manes/caffeine",
        "3rdparty/jvm/org/openjdk/jmh:jmh-core",
        "3rdparty/jvm/org/scala-lang:scala-library",
        "util/util-core:scala",
        "util/util-hashing/src/main/scala",
        "util/util-reflect/src/main/scala/com/twitter/util/reflect",
        "util/util-stats/src/main/scala",
    ],
)

jvm_binary(
    name = "jmh",
    main = "org.openjdk.jmh.Main",
    dependencies = [
        ":scala",
    ],
)
