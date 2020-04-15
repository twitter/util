junit_tests(
    sources = globs("*.scala"),
    compiler_option_sets = ["fatal_warnings"],
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/mockito:mockito-all",
        "3rdparty/jvm/org/scalatest",
        "util/util-routing/src/main/scala/com/twitter/util/routing",
        "util/util-routing/src/test/scala/com/twitter/util/routing/dynamic",
        "util/util-routing/src/test/scala/com/twitter/util/routing/simple",
    ],
)
