scala_library(
    sources = rglobs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
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
