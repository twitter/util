scala_library(
    # This target exists only to act as an aggregator jar.
    sources = ["PantsWorkaroundCore.scala"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-core",
        repo = artifactory,
    ),
    dependencies = [
        "util/util-core/src/main/scala/com/twitter/concurrent",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-core/src/main/scala/com/twitter/io",
        "util/util-core/src/main/scala/com/twitter/io/exp",
        "util/util-core/src/main/scala/com/twitter/util",
    ],
    exports = [
        "util/util-core/src/main/scala/com/twitter/concurrent",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-core/src/main/scala/com/twitter/io",
        "util/util-core/src/main/scala/com/twitter/io/exp",
        "util/util-core/src/main/scala/com/twitter/util",
    ],
)
