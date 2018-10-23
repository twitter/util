scala_library(
    # This target exists only to act as an aggregator jar.
    sources = ["PantsWorkaround.scala"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-collection",
        repo = artifactory,
    ),
    dependencies = [
        "util/util-collection/src/main/scala/com/twitter/collection",
        "util/util-collection/src/main/scala/com/twitter/util",
    ],
    exports = [
        "util/util-collection/src/main/scala/com/twitter/collection",
        "util/util-collection/src/main/scala/com/twitter/util",
    ],
)
