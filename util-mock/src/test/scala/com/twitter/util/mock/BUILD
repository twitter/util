junit_tests(
    sources = ["**/*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    tags = ["non-exclusive"],
    dependencies = [
        "3rdparty/jvm/org/mockito:mockito-core",
        "3rdparty/jvm/org/mockito:mockito-scala",
        "3rdparty/jvm/org/mockito:mockito-scala-scalatest",
        "3rdparty/jvm/org/scalatest",
        "3rdparty/jvm/org/scalatestplus:junit",
        "util/util-core:scala",
        "util/util-mock/src/main/scala/com/twitter/util/mock",
        "util/util-mock/src/test/java/com/twitter/util/mock",
    ],
)
