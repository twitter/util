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
        "3rdparty/jvm/org/scalatestplus:junit",
        "util/util-jvm/src/main/scala/com/twitter/jvm",
    ],
)
