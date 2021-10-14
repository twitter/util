scala_library(
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-zk-test",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/org/apache/zookeeper:zookeeper-server",
    ],
    exports = [
        "3rdparty/jvm/org/apache/zookeeper:zookeeper-server",
    ],
)
