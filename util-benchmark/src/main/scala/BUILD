scala_library(
    sources = [
        "**/*.java",
        "**/*.scala",
    ],
    compiler_option_sets = ["fatal_warnings"],
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/com/github/ben-manes/caffeine",
        "3rdparty/jvm/jakarta/validation:jakarta.validation-api",
        "3rdparty/jvm/org/openjdk/jmh:jmh-core",
        "3rdparty/jvm/org/scala-lang:scala-library",
        "util/util-core:scala",
        "util/util-hashing/src/main/scala",
        "util/util-jackson/src/main/scala/com/twitter/util/jackson",
        "util/util-reflect/src/main/scala/com/twitter/util/reflect",
        "util/util-slf4j-api/src/main/scala/com/twitter/util/logging",
        "util/util-stats/src/main/scala",
        "util/util-validator/src/main/scala/com/twitter/util/validation",
    ],
)

jvm_binary(
    name = "jmh",
    main = "org.openjdk.jmh.Main",
    tags = ["bazel-compatible"],
    dependencies = [
        ":scala",
    ],
)
