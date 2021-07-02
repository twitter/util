junit_tests(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    tags = [
        "bazel-compatible",
        "non-exclusive",
    ],
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/scalacheck",
        "3rdparty/jvm/org/scalatest",
        "3rdparty/jvm/org/scalatestplus:junit",
        "3rdparty/jvm/org/scalatestplus:scalacheck",
        "util/util-core:util-core-util",
        "util/util-core/src/main/scala/com/twitter/io",
        "util/util-security-test-certs/src/main/resources",
        "util/util-security/src/main/scala/com/twitter/util/security",
    ],
)
