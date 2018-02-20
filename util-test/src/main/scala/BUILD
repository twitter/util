scala_library(
    sources = rglobs("*.scala"),
    fatal_warnings = True,
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-test",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/org/mockito:mockito-all",
        "3rdparty/jvm/org/scalatest",
        "util/util-logging/src/main/scala",
    ],
    exports = [
        "3rdparty/jvm/org/scalatest",
    ],
)
