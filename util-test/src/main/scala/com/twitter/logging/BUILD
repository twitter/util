scala_library(
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-test-logging",
        repo = artifactory,
    ),
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/org/scalatest",
        "util/util-logging/src/main/scala/com/twitter/logging",
    ],
    exports = [
        "3rdparty/jvm/org/scalatest",
    ],
)
