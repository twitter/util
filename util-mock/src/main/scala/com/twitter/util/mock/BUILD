scala_library(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-mockito",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/org/mockito:mockito-core",
        "3rdparty/jvm/org/mockito:mockito-scala",
    ],
    exports = [
        "3rdparty/jvm/org/mockito:mockito-scala",
    ],
)
