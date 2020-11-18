junit_tests(
    sources = ["**/*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    tags = ["non-exclusive"],
    dependencies = [
        "3rdparty/jvm/com/google/inject:guice",
        "3rdparty/jvm/com/google/inject/extensions:guice-testlib",
        "3rdparty/jvm/javax/inject:javax.inject",
        "3rdparty/jvm/junit",
        "3rdparty/jvm/net/codingwell:scala-guice",
        "3rdparty/jvm/org/scalatest",
        "3rdparty/jvm/org/scalatestplus:junit",
        "3rdparty/jvm/org/scalatestplus:scalacheck",
        "util/util-inject/src/main/scala/com/twitter/util/inject",
        "util/util-inject/src/test/java/com/twitter/util/inject",
    ],
)
