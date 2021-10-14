junit_tests(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/scalatest",
        "3rdparty/jvm/org/scalatestplus:junit",
        "util/util-core:util-core-util",
        "util/util-reflect/src/main/scala/com/twitter/util/reflect",
        "util/util-reflect/src/test/java/com/twitter/util/reflect",
    ],
)
