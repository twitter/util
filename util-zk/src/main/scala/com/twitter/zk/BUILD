scala_library(
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-zk-zk",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/org/apache/zookeeper:zookeeper-client",
        "util/util-core/src/main/scala/com/twitter/concurrent",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-core:util-core-util",
        "util/util-logging/src/main/scala/com/twitter/logging",
    ],
    exports = [
        "3rdparty/jvm/org/apache/zookeeper:zookeeper-client",
        "util/util-core/src/main/scala/com/twitter/concurrent",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-core:util-core-util",
    ],
)
