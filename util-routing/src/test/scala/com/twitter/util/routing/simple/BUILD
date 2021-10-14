scala_library(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "util/util-core:util-core-util",
        "util/util-logging/src/main/scala/com/twitter/logging",
        "util/util-routing/src/main/scala/com/twitter/util/routing",
    ],
    exports = [
        "util/util-logging/src/main/scala/com/twitter/logging",
    ],
)
