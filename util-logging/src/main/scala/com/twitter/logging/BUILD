scala_library(
    sources = rglobs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-logging",
        repo = artifactory,
    ),
    dependencies = [
        "util/util-app/src/main/scala",
        "util/util-core/src/main/scala/com/twitter/concurrent",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-core/src/main/scala/com/twitter/util",
        "util/util-stats/src/main/scala/com/twitter/finagle/stats",
    ],
    exports = [
        "util/util-app/src/main/scala",
    ],
)
