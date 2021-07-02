java_library(
    compiler_option_sets = ["fatal_warnings"],
    provides = artifact(
        org = "com.twitter",
        name = "util-core-java-io",
        repo = artifactory,
    ),
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/org/scala-lang:scala-library",
        "util/util-core:scala",
    ],
    exports = [
        "3rdparty/jvm/org/scala-lang:scala-library",
        "util/util-core:scala",
    ],
)
