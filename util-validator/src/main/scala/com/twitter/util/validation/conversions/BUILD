scala_library(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-validation-conversions",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/jakarta/validation:jakarta.validation-api",
        "3rdparty/jvm/org/hibernate/validator:hibernate-validator",
        "util/util-validator/src/main/scala/com/twitter/util/validation/engine",
    ],
    exports = [
        "3rdparty/jvm/jakarta/validation:jakarta.validation-api",
        "3rdparty/jvm/org/hibernate/validator:hibernate-validator",
    ],
)
