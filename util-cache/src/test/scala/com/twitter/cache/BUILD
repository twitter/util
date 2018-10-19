junit_tests(
    sources = globs(
        "*.scala",
        exclude = [
            globs(
                "AbstractFutureCacheTest.scala",
                "AbstractLoadingFutureCacheTest.scala",
            ),
        ],
    ),
    compiler_option_sets = {"fatal_warnings"},
    dependencies = [
        ":abstract_tests",
        "3rdparty/jvm/com/github/ben-manes/caffeine",
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/mockito:mockito-all",
        "3rdparty/jvm/org/scalatest",
        "util/util-cache/src/main/scala/com/twitter/cache",
        "util/util-cache/src/main/scala/com/twitter/cache/caffeine",
        "util/util-core/src/main/scala/com/twitter/util",
    ],
)

scala_library(
    name = "abstract_tests",
    sources = [
        "AbstractFutureCacheTest.scala",
        "AbstractLoadingFutureCacheTest.scala",
    ],
    compiler_option_sets = {"fatal_warnings"},
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/scalatest",
        "util/util-cache/src/main/scala/com/twitter/cache",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-core/src/main/scala/com/twitter/util",
    ],
)
