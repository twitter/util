scala_library(
    sources = rglobs("*.scala"),
    fatal_warnings = True,
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-slf4j",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/org/slf4j:slf4j-api",
    ],
    exports = [
        "3rdparty/jvm/org/slf4j:slf4j-api",
    ],
)
