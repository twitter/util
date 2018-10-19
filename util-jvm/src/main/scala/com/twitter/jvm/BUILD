scala_library(
    compiler_option_sets = {"fatal_warnings"},
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-jvm",
        repo = artifactory,
    ),
    dependencies = [
        "util/util-app/src/main/scala/com/twitter/app",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-core/src/main/scala/com/twitter/util",
        "util/util-stats/src/main/scala/com/twitter/finagle/stats",
    ],
    exports = [
        "util/util-core/src/main/scala/com/twitter/util",
    ],
)
