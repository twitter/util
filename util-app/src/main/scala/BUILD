scala_library(
    sources = rglobs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-app",
        repo = artifactory,
    ),
    dependencies = [
        "util/util-app/src/main/java/com/twitter/app",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-core/src/main/scala/com/twitter/util",
        "util/util-registry/src/main/scala/com/twitter/util/registry",
    ],
    exports = [
        "util/util-app/src/main/java/com/twitter/app",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-core/src/main/scala/com/twitter/util",
    ],
)
