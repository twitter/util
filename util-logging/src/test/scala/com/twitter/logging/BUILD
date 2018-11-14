junit_tests(
    sources = globs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/mockito:mockito-all",
        "3rdparty/jvm/org/scalatest",
        "util/util-app/src/main/scala",
        "util/util-core/src/main/scala/com/twitter/io",
        "util/util-core/src/main/scala/com/twitter/util",
        "util/util-logging/src/main/scala/com/twitter/logging",
        "util/util-stats/src/main/scala/com/twitter/finagle/stats",
    ],
)
