java_library(
    name = "object_size_calculator",
    sources = ["ObjectSizeCalculator.java"],
    compiler_option_sets = {"fatal_warnings"},
    dependencies = [
        "3rdparty/jvm/com/github/ben-manes/caffeine",
    ],
)

junit_tests(
    sources = [
        "!ObjectSizeCalculator.java",
        "*.java",
    ],
    compiler_option_sets = {"fatal_warnings"},
    dependencies = [
        ":object_size_calculator",
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/scala-lang:scala-library",
        "util/util-core/src/main/java/com/twitter/util",
        "util/util-core/src/main/scala/com/twitter/util",
    ],
)
