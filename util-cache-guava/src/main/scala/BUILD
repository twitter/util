scala_library(
    sources = rglobs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-cache-guava",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/com/google/guava",
        "util/util-cache/src/main/scala",
        "util/util-core/src/main/scala",
    ],
)
