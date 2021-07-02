scala_library(
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-thrift",
        repo = artifactory,
    ),
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind",
        "3rdparty/jvm/org/apache/thrift:libthrift",
        "util/util-codec/src/main/scala/com/twitter/util",
        "util/util-core:util-core-util",
    ],
    exports = [
        "3rdparty/jvm/org/apache/thrift:libthrift",
        "util/util-codec/src/main/scala/com/twitter/util",
        "util/util-core:util-core-util",
    ],
)
