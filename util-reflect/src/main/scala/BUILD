scala_library(
    sources = rglobs("*.scala"),
    fatal_warnings = True,
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-reflect",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/cglib",
        "util/util-core/src/main/scala",
    ],
    exports = [
        "3rdparty/jvm/cglib",
    ],
)
