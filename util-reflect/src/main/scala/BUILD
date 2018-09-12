scala_library(
    sources = rglobs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
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
