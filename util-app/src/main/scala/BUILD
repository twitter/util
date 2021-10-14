scala_library(
    sources = ["**/*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-app",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "util/util-app-lifecycle/src/main/scala/com/twitter/app/lifecycle",
        "util/util-app/src/main/java/com/twitter/app",
        "util/util-core:util-core-util",
        "util/util-core/src/main/scala/com/twitter/concurrent",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-core/src/main/scala/com/twitter/io",
        "util/util-registry/src/main/scala/com/twitter/util/registry",
    ],
    exports = [
        "util/util-app-lifecycle/src/main/scala/com/twitter/app/lifecycle",
        "util/util-app/src/main/java/com/twitter/app",
        "util/util-core:util-core-util",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-core/src/main/scala/com/twitter/io",
    ],
)
