scala_library(
    # This target exists only to act as an aggregator jar.
    sources = ["PantsWorkaround.scala"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-zk",
        repo = artifactory,
    ),
    dependencies = [
        "util/util-zk/src/main/scala/com/twitter/zk",
        "util/util-zk/src/main/scala/com/twitter/zk/coordination",
    ],
    exports = [
        "util/util-zk/src/main/scala/com/twitter/zk",
        "util/util-zk/src/main/scala/com/twitter/zk/coordination",
    ],
)
