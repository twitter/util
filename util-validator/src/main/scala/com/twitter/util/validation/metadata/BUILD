scala_library(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-validation-metadata",
        repo = artifactory,
    ),
    strict_deps = True,
    dependencies = [
        "3rdparty/jvm/com/github/ben-manes/caffeine",
        "3rdparty/jvm/jakarta/validation:jakarta.validation-api",
        "3rdparty/jvm/org/hibernate/validator:hibernate-validator",
        "3rdparty/jvm/org/json4s:json4s-core",
        "util/util-core:scala",
        "util/util-reflect/src/main/scala/com/twitter/util/reflect",
        "util/util-slf4j-api/src/main/scala",
    ],
)
