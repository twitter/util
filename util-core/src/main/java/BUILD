java_library(
    sources = rglobs("*.java"),
    fatal_warnings = True,
    provides = artifact(
        org = "com.twitter",
        name = "util-core-java",
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
