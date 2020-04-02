scala_library(
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-cache-caffeine",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/com/github/ben-manes/caffeine",
        "util/util-cache/src/main/scala/com/twitter/cache",
        "util/util-core:util-core-util",
    ],
)
