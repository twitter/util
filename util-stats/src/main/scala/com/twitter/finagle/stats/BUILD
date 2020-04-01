scala_library(
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-stats",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/com/fasterxml/jackson:jackson-module-scala",
        "3rdparty/jvm/com/fasterxml/jackson/core:jackson-core",
        "3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind",
        "3rdparty/jvm/com/fasterxml/jackson/datatype:jackson-datatype-joda",
        "3rdparty/jvm/com/fasterxml/jackson/module:jackson-module-scala",
        "3rdparty/jvm/com/github/ben-manes/caffeine",
        "3rdparty/jvm/com/google/code/findbugs:jsr305",
        "util/util-core/src/main/scala/com/twitter/util",
        "util/util-lint/src/main/scala/com/twitter/util/lint",
    ],
    exports = [
        "3rdparty/jvm/com/github/ben-manes/caffeine",
    ],
)
