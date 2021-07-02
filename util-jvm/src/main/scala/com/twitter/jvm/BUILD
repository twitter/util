scala_library(
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-jvm",
        repo = artifactory,
    ),
    tags = ["bazel-compatible"],
    dependencies = [
        "util/util-app/src/main/scala",
        "util/util-core:util-core-util",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-stats/src/main/scala/com/twitter/finagle/stats",
    ],
    exports = [
        "util/util-core:util-core-util",
    ],
)
