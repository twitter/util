scala_library(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-inject-internal",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/com/google/inject:guice",
        "3rdparty/jvm/javax/inject:javax.inject",
        "util/util-app/src/main/scala",
        "util/util-core:util-core-util",
        "util/util-inject/src/main/java/com/twitter/util/inject/flag",
        "util/util-inject/src/main/scala/com/twitter/util/inject",
        "util/util-slf4j-api/src/main/scala",
    ],
    exports = [
        "util/util-app/src/main/scala",
        "util/util-inject/src/main/java/com/twitter/util/inject/flag",
        "util/util-slf4j-api/src/main/scala",
    ],
)
