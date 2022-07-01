java_library(
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    provides = artifact(
        org = "com.twitter",
        name = "util-core-java-concurrent",
        repo = artifactory,
    ),
    strict_deps = True,
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
