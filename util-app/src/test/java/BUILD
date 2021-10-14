junit_tests(
    sources = ["**/*.java"],
    compiler_option_sets = ["fatal_warnings"],
    strict_deps = True,
    tags = [
        "bazel-compatible",
        "non-exclusive",
    ],
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/scala-lang:scala-library",
        "3rdparty/jvm/org/scalatestplus:junit",
        "util/util-app/src/main/java/com/twitter/app",
        "util/util-app/src/main/scala",
        "util/util-core:util-core-util",
        "util/util-function/src/main/java/com/twitter/function",
    ],
)
