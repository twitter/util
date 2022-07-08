scala_library(
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-cache-guava",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/com/google/guava",
        "util/util-cache/src/main/scala/com/twitter/cache",
        "util/util-core:util-core-util",
    ],
)
