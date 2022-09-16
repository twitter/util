COMMON_DEPS = [
    "3rdparty/jvm/junit",
    "3rdparty/jvm/org/mockito:mockito-core",
    "3rdparty/jvm/org/scalatest",
    "3rdparty/jvm/org/scalatestplus:mockito",
    "util/util-app/src/main/scala",
    "util/util-core:util-core-util",
    "util/util-jvm/src/main/scala/com/twitter/jvm",
]

junit_tests(
    sources = [
        "!**/EstimatorApp.scala",
        "**/*.scala",
    ],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    strict_deps = True,
    tags = [
        "bazel-compatible",
        "non-exclusive",
    ],
    dependencies = COMMON_DEPS + [
        "3rdparty/jvm/org/scalatestplus:junit",
        "util/util-stats/src/main/scala/com/twitter/finagle/stats:stats",
    ],
)

scala_library(
    name = "estimator_app",
    # Overlap the sources (except EstimatorApp.scala) with the above target for the following reasons:
    # 1) We cannot extract a common `scala_library` because Pants does not like `junit_tests`
    #    depending on a `scala_library` containing the test sources while the `junit_test`
    #    itself has empty sources.
    # 2) Bazel strictly forbids `scala_library` to depend back onto a test target, whereas Pants
    #    does not strictly enforce it.
    sources = ["**/*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = COMMON_DEPS + ["util/util-stats/src/main/scala/com/twitter/finagle/stats:stats"],
)

jvm_binary(
    name = "bin",
    main = "com.twitter.jvm.EstimatorApp",
    platform = "java8",
    tags = ["bazel-compatible"],
    dependencies = [":estimator_app"],
)
