junit_tests(
    sources = [
        "!AbstractFutureCacheTest.scala",
        "!AbstractLoadingFutureCacheTest.scala",
        "*.scala",
    ],
    compiler_option_sets = ["fatal_warnings"],
    tags = ["non-exclusive"],
    dependencies = [
        ":abstract_tests",
        "3rdparty/jvm/com/github/ben-manes/caffeine",
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/mockito:mockito-core",
        "3rdparty/jvm/org/scalatest",
        "3rdparty/jvm/org/scalatestplus:junit",
        "3rdparty/jvm/org/scalatestplus:mockito-3-2",
        "util/util-cache/src/main/scala/com/twitter/cache",
        "util/util-cache/src/main/scala/com/twitter/cache/caffeine",
        "util/util-core:util-core-util",
        "util/util-core/src/main/scala/com/twitter/conversions",
    ],
)

scala_library(
    name = "abstract_tests",
    sources = [
        "AbstractFutureCacheTest.scala",
        "AbstractLoadingFutureCacheTest.scala",
    ],
    compiler_option_sets = ["fatal_warnings"],
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/scalatest",
        "util/util-cache/src/main/scala/com/twitter/cache",
        "util/util-core:util-core-util",
        "util/util-core/src/main/scala/com/twitter/conversions",
    ],
)
