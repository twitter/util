junit_tests(
    sources = ["**/*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    strict_deps = True,
    tags = [
        "bazel-compatible",
        "non-exclusive",
    ],
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/mockito:mockito-core",
        "3rdparty/jvm/org/scalatest",
        "3rdparty/jvm/org/scalatestplus:junit",
        "util/util-app/src/main/scala",
        "util/util-core:util-core-util",
        "util/util-core/src/main/scala/com/twitter/io",
        "util/util-logging/src/main/scala/com/twitter/logging",
        "util/util-stats/src/main/scala/com/twitter/finagle/stats",
    ],
)
