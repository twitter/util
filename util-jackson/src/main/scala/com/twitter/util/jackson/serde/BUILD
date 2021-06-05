scala_library(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-jackson-serde",
        repo = artifactory,
    ),
    strict_deps = True,
    dependencies = [
        "3rdparty/jvm/com/fasterxml/jackson/core:jackson-core",
        "3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind",
        "3rdparty/jvm/com/fasterxml/jackson/module:jackson-module-scala",
        "3rdparty/jvm/org/json4s:json4s-core",
        "util/util-core:util-core-util",
        "util/util-jackson/src/main/scala/com/twitter/util/jackson/caseclass",
        "util/util-jackson/src/main/scala/com/twitter/util/jackson/caseclass/exceptions",
    ],
)
