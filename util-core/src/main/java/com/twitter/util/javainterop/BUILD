java_library(
    compiler_option_sets = ["fatal_warnings"],
    provides = artifact(
        org = "com.twitter",
        name = "util-core-java-javainterop",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/org/scala-lang:scala-library",
    ],
    exports = [
        "3rdparty/jvm/org/scala-lang:scala-library",
    ],
)
