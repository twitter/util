scala_library(
    sources = rglobs("*.scala"),
    fatal_warnings = True,
    dependencies = [
        "3rdparty/jvm/com/github/ben-manes/caffeine",
        "3rdparty/jvm/org/openjdk/jmh:jmh-core",
        "3rdparty/jvm/org/scala-lang:scala-library",
        "util/util-core/src/main/scala",
        "util/util-hashing/src/main/scala",
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
