scala_library(
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-routing",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "util/util-core:util-core-util",
        "util/util-slf4j-api/src/main/scala/com/twitter/util/logging",
    ],
    exports = ["util/util-slf4j-api/src/main/scala/com/twitter/util/logging"],
)
