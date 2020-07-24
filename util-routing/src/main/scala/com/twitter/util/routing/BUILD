scala_library(
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-routing",
        repo = artifactory,
    ),
    dependencies = [
        "util/util-core:util-core-util",
        "util/util-logging/src/main/scala",
    ],
    exports = ["util/util-logging/src/main/scala"],
)
