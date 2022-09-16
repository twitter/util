# workaround for https://github.com/bazelbuild/bazel/issues/3864
alias(
    name = "logging",
    target = ":internal",
    tags = [
        "bazel-compatible",
        "visibility://codestructure/packages/util-logging:legal",
    ],
)

scala_library(
    name = "internal",
    sources = ["**/*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-logging",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = [
        "bazel-compatible",
    ],
    dependencies = [
        "util/util-app/src/main/scala",
        "util/util-core:util-core-util",
        "util/util-core/src/main/scala/com/twitter/concurrent",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-stats/src/main/scala/com/twitter/finagle/stats",
    ],
    exports = [
        "util/util-app/src/main/scala",
    ],
)
