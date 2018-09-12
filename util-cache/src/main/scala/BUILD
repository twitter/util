scala_library(
    sources = rglobs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-cache",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/com/github/ben-manes/caffeine",
        "util/util-core/src/main/scala",
    ],
    exports = [
        "util/util-core/src/main/scala",
    ],
)
