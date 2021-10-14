junit_tests(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    strict_deps = True,
    tags = [
        "bazel-compatible",
        "non-exclusive",
    ],
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/apache/zookeeper:zookeeper-client",
        "3rdparty/jvm/org/mockito:mockito-core",
        "3rdparty/jvm/org/scalatest",
        "3rdparty/jvm/org/scalatestplus:junit",
        "3rdparty/jvm/org/scalatestplus:mockito-3-3",
        "util/util-core:util-core-util",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-logging/src/main/scala/com/twitter/logging",
        "util/util-zk/src/main/scala/com/twitter/zk",
    ],
)
