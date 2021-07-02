scala_library(
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-zk-coordination",
        repo = artifactory,
    ),
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/org/apache/zookeeper:zookeeper-client",
        "util/util-core:util-core-util",
        "util/util-core/src/main/scala/com/twitter/concurrent",
        "util/util-zk/src/main/scala/com/twitter/zk",
    ],
    exports = [
        "3rdparty/jvm/org/apache/zookeeper:zookeeper-client",
        "util/util-core:util-core-util",
        "util/util-core/src/main/scala/com/twitter/concurrent",
        "util/util-zk/src/main/scala/com/twitter/zk",
    ],
)
