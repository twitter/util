junit_tests(
    sources = [
        "!EstimatorApp.scala",
        "*.scala",
    ],
    compiler_option_sets = ["fatal_warnings"],
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/mockito:mockito-all",
        "3rdparty/jvm/org/scalatest",
        "util/util-app/src/main/scala",
        "util/util-core:util-core-util",
        "util/util-jvm/src/main/scala/com/twitter/jvm",
        "util/util-logging/src/main/scala",
        "util/util-test/src/main/scala",
    ],
)

scala_library(
    name = "estimator_app",
    sources = ["EstimatorApp.scala"],
    compiler_option_sets = ["fatal_warnings"],
    dependencies = [
        "util/util-jvm/src/main/scala/com/twitter/jvm",
    ],
)

jvm_binary(
    name = "bin",
    main = "com.twitter.jvm.EstimatorApp",
    dependencies = [
        ":estimator_app",
    ],
)
