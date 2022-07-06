junit_tests(
    sources = ["**/*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    strict_deps = True,
    tags = [
        "bazel-compatible",
        "known-to-fail-jira:CSL-12163",
        "non-exclusive",
    ],
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/mockito:mockito-core",
        "3rdparty/jvm/org/scalatest",
        "3rdparty/jvm/org/scalatestplus:junit",
        "3rdparty/jvm/org/scalatestplus:mockito-3-3",
        "util/util-app/src/main/scala",
        "util/util-app/src/test/resources",
        "util/util-core:util-core-util",
        "util/util-core/src/main/scala/com/twitter/concurrent",
        "util/util-registry/src/main/scala/com/twitter/util/registry",
    ],
)
