scala_library(
    sources = rglobs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-jvm",
        repo = artifactory,
    ),
    dependencies = [
        "util/util-app/src/main/scala",
        "util/util-core/src/main/scala",
        "util/util-stats/src/main/scala",
    ],
    exports = [
        "util/util-core/src/main/scala",
    ],
)
