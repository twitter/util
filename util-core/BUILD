target(
    dependencies = [
        ":scala",
        "util/util-core/src/main/java",
    ],
)

target(
    name = "tests",
    dependencies = [
        "util/util-core/src/test/java",
        "util/util-core/src/test/scala",
    ],
)

# (DPB-10452) This target definition lives here because it needs to accommodate util-core
# code resolution in IntelliJ as well as `util-core-util` which contains packages
# com.twitter.concurrent and com.twitter.util
scala_library(
    name = "util-core-util",
    sources = [
        "concurrent-extra/src/main/scala/com/twitter/concurrent/*.scala",
        "src/main/scala-2.12-/com/twitter/util/*.scala",
        "src/main/scala/com/twitter/util/*.scala",
    ],
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-core-util",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/org/scala-lang:scala-reflect",
        "3rdparty/jvm/org/scala-lang/modules:scala-collection-compat",
        "3rdparty/jvm/org/scala-lang/modules:scala-parser-combinators",
        "util/util-function/src/main/java",
    ],
    exports = [
        "3rdparty/jvm/org/scala-lang/modules:scala-collection-compat",
        "3rdparty/jvm/org/scala-lang/modules:scala-parser-combinators",
        "util/util-function/src/main/java",
    ],
)

scala_library(
    # This target exists only to act as an aggregator jar.
    name = "scala",
    sources = ["PantsWorkaroundCore.scala"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-core",
        repo = artifactory,
    ),
    dependencies = [
        ":util-core-util",
        "util/util-core/src/main/scala/com/twitter/concurrent",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-core/src/main/scala/com/twitter/io",
        "util/util-core/src/main/scala/com/twitter/io/exp",
    ],
    exports = [
        ":util-core-util",
        "util/util-core/src/main/scala/com/twitter/concurrent",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-core/src/main/scala/com/twitter/io",
        "util/util-core/src/main/scala/com/twitter/io/exp",
    ],
)
