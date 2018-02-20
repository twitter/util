scala_library(
    sources = rglobs("*.scala"),
    fatal_warnings = True,
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-zk",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/org/apache/zookeeper:zookeeper-client",
        "util/util-core/src/main/scala",
        "util/util-logging/src/main/scala",
    ],
    exports = [
        "3rdparty/jvm/org/apache/zookeeper:zookeeper-client",
        "util/util-core/src/main/scala",
    ],
)
