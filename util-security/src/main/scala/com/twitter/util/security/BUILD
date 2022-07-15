scala_library(
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-security",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/org/yaml:snakeyaml",
        "util/util-core:util-core-util",
        "util/util-logging/src/main/scala/com/twitter/logging",
    ],
)
