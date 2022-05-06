scala_library(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-validation-internal-validators",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/jakarta/validation:jakarta.validation-api",
        "3rdparty/jvm/org/hibernate/validator:hibernate-validator",
        "util/util-core:scala",
        "util/util-slf4j-api/src/main/scala/com/twitter/util/logging",
        "util/util-validator-constraints/src/main/java/com/twitter/util/validation/constraints",
        "util/util-validator/src/main/scala/com/twitter/util/validation/constraintvalidation",
    ],
)
