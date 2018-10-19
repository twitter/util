scala_library(
    # This target exists only to act as an aggregator jar.
    sources = ["BUILD"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-cache",
        repo = artifactory,
    ),
    dependencies = [
        "util/util-cache/src/main/scala/com/twitter/cache",
        "util/util-cache/src/main/scala/com/twitter/cache/caffeine",
        "util/util-core/src/main/scala/com/twitter/util",
    ],
    exports = [
        "util/util-cache/src/main/scala/com/twitter/cache",
        "util/util-cache/src/main/scala/com/twitter/cache/caffeine",
        "util/util-core/src/main/scala/com/twitter/util",
    ],
)
