scala_library(
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-test-testing",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/org/mockito:mockito-all",
        "3rdparty/jvm/org/scalatest",
        "3rdparty/jvm/org/scalatestplus:mockito-1-10",
        "util/util-stats/src/main/scala",
        "util/util-stats/src/main/scala/com/twitter/finagle/stats",
    ],
    exports = [
        "3rdparty/jvm/org/scalatest",
    ],
)
