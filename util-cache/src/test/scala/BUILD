junit_tests(
    sources = rglobs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
    dependencies = [
        "3rdparty/jvm/com/github/ben-manes/caffeine",
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/mockito:mockito-all",
        "3rdparty/jvm/org/scalatest",
        "util/util-cache",
        "util/util-core/src/main/scala",
    ],
)

scala_library(
    name = "abstract_tests",
    sources = [
        "com/twitter/cache/AbstractFutureCacheTest.scala",
        "com/twitter/cache/AbstractLoadingFutureCacheTest.scala",
    ],
    compiler_option_sets = {"fatal_warnings"},
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/mockito:mockito-all",
        "3rdparty/jvm/org/scalatest",
        "util/util-cache",
        "util/util-core/src/main/scala",
    ],
)
