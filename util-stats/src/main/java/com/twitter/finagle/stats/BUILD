java_library(
    sources = ["*.java"],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    provides = artifact(
        org = "com.twitter",
        name = "util-stats-java",
        repo = artifactory,
    ),
    tags = ["bazel-compatible"],
    dependencies = [
        "util/util-stats",
    ],
)
