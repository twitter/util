java_library(
    compiler_option_sets = {"fatal_warnings"},
    provides = artifact(
        org = "com.twitter",
        name = "util-core-java-concurrent",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/org/scala-lang:scala-library",
        "util/util-core/src/main/scala",
    ],
    exports = [
        "3rdparty/jvm/org/scala-lang:scala-library",
        "util/util-core/src/main/scala",
    ],
)
