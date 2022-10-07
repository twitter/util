junit_tests(
    sources = ["*.java"],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    strict_deps = True,
    tags = [
        "bazel-compatible",
        "non-exclusive",
    ],
    dependencies = [
        "3rdparty/jvm/com/google/guava",
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/scala-lang:scala-library",
        "3rdparty/jvm/org/scalatestplus:junit",
        "util/util-cache-guava/src/main/scala/com/twitter/cache/guava",
        "util/util-cache/src/main/scala/com/twitter/cache",
        "util/util-core:util-core-util",
    ],
)
