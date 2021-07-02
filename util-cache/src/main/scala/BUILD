scala_library(
    # This target exists only to act as an aggregator jar.
    sources = ["PantsWorkaroundCache.scala"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-cache",
        repo = artifactory,
    ),
    tags = ["bazel-compatible"],
    dependencies = [
        "util/util-cache/src/main/scala/com/twitter/cache",
        "util/util-cache/src/main/scala/com/twitter/cache/caffeine",
        "util/util-core:util-core-util",
    ],
    exports = [
        "util/util-cache/src/main/scala/com/twitter/cache",
        "util/util-cache/src/main/scala/com/twitter/cache/caffeine",
        "util/util-core:util-core-util",
    ],
)
