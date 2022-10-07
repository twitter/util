scala_library(
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
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
        "util/util-stats/src/main/scala",
        "util/util-stats/src/main/scala/com/twitter/finagle/stats",
    ],
    exports = [
        "3rdparty/jvm/org/scalatest",
    ],
)

scala_library(
    name = "no-mockito",
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-test-testing-no-mockito",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = ["bazel-compatible"],
    sources = ["*.scala", "!ArgumentCapture.scala"],
    dependencies = [
        "3rdparty/jvm/org/scalatest",
        "util/util-stats/src/main/scala",
        "util/util-stats/src/main/scala/com/twitter/finagle/stats",
    ],
    exports = [
        "3rdparty/jvm/org/scalatest",
    ],
)
