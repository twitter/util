scala_library(
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-cache-guava",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/com/google/guava",
        "util/util-cache/src/main/scala/com/twitter/cache",
        "util/util-core:util-core-util",
    ],
)
