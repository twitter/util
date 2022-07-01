scala_library(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-validation-executable",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/jakarta/validation:jakarta.validation-api",
        "util/util-validator/src/main/scala/com/twitter/util/validation/metadata",
    ],
    exports = [
        "3rdparty/jvm/jakarta/validation:jakarta.validation-api",
        "util/util-validator/src/main/scala/com/twitter/util/validation/metadata",
    ],
)
