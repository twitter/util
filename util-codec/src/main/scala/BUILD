scala_library(
    sources = rglobs("*.scala"),
    fatal_warnings = True,
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-codec",
        repo = artifactory,
    ),
    dependencies = [
        "util/util-core/src/main/scala",
    ],
)
