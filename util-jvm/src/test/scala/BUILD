junit_tests(
    sources = rglobs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/mockito:mockito-all",
        "3rdparty/jvm/org/scalatest",
        "util/util-app/src/main/scala",
        "util/util-core/src/main/scala",
        "util/util-jvm/src/main/scala",
        "util/util-logging/src/main/scala",
        "util/util-test/src/main/scala",
    ],
)

jvm_binary(
    name = "bin",
    main = "com.twitter.jvm.EstimatorApp",
    dependencies = [
        ":scala",
    ],
)
