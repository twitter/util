scala_library(
    sources = rglobs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
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
