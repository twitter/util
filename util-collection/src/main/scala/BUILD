scala_library(
    sources = rglobs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-collection",
        repo = artifactory,
    ),
    dependencies = [
        "util/util-core/src/main/scala",
    ],
    exports = [
        "util/util-core/src/main/scala",
    ],
)
