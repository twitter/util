junit_tests(
    sources = ["**/*.java"],
    platform = "java8",
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/com/fasterxml/jackson/dataformat:jackson-dataformat-yaml",
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/scalatestplus:junit",
        "util/util-jackson/src/main/scala/com/twitter/util/jackson",
    ],
)
