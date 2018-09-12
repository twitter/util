scala_library(
    sources = rglobs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-thrift",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind",
        "3rdparty/jvm/org/apache/thrift:libthrift",
        "util/util-codec",
        "util/util-core/src/main/scala",
    ],
    exports = [
        "3rdparty/jvm/org/apache/thrift:libthrift",
        "util/util-codec",
    ],
)
