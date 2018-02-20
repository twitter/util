scala_library(
    sources = rglobs("*.scala"),
    fatal_warnings = True,
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-zk-test",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/org/apache/zookeeper:zookeeper-server",
    ],
    exports = [
        "3rdparty/jvm/org/apache/zookeeper:zookeeper-server",
    ],
)
