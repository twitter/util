java_library(
    name = "object_size_calculator",
    sources = ["ObjectSizeCalculator.java"],
    compiler_option_sets = ["fatal_warnings"],
    dependencies = [
        "3rdparty/jvm/com/github/ben-manes/caffeine",
        "3rdparty/jvm/com/google/errorprone:error_prone_annotations",
    ],
)

junit_tests(
    sources = [
        "!ObjectSizeCalculator.java",
        "*.java",
    ],
    compiler_option_sets = ["fatal_warnings"],
    tags = ["non-exclusive"],
    dependencies = [
        ":object_size_calculator",
        "3rdparty/jvm/com/github/ben-manes/caffeine",
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/scala-lang:scala-library",
        "3rdparty/jvm/org/scalatestplus:junit",
        "util/util-core:util-core-util",
        "util/util-core/src/main/java/com/twitter/util",
    ],
)
