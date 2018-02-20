scala_library(
    sources = rglobs("*.scala"),
    fatal_warnings = True,
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-app",
        repo = artifactory,
    ),
    dependencies = [
        "util/util-app/src/main/java",
        "util/util-core/src/main/scala",
        "util/util-registry/src/main/scala",
    ],
    exports = [
        "util/util-app/src/main/java",
        "util/util-core/src/main/scala",
    ],
)
