scala_library(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-validation-internal-engine",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/jakarta/validation:jakarta.validation-api",
        "3rdparty/jvm/org/hibernate/validator:hibernate-validator",
        "3rdparty/jvm/org/scala-lang/modules:scala-collection-compat",
        "util/util-validator-constraints/src/main/java/com/twitter/util/validation",
        "util/util-validator/src/main/scala/com/twitter/util/validation/internal/metadata/descriptor",
        "util/util-validator/src/main/scala/org/hibernate/validator/internal/engine",
    ],
)
