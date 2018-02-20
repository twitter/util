scala_library(
    sources = rglobs("*.scala"),
    fatal_warnings = True,
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-stats",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/com/github/ben-manes/caffeine",
        "3rdparty/jvm/com/google/code/findbugs:jsr305",
        "util/util-core/src/main/scala",
        "util/util-lint/src/main/scala",
    ],
    exports = [
        "3rdparty/jvm/com/github/ben-manes/caffeine",
    ],
)
