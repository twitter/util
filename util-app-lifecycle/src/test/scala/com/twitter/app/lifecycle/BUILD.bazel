junit_tests(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    strict_deps = True,
    tags = [
        "bazel-compatible",
        "non-exclusive",
    ],
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/scalatest",
        "3rdparty/jvm/org/scalatestplus:junit",
        "util/util-app-lifecycle/src/main/scala/com/twitter/app/lifecycle",
        "util/util-core:util-core-util",
    ],
)
