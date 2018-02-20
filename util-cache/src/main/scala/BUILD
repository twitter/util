scala_library(
    sources = rglobs("*.scala"),
    fatal_warnings = True,
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
