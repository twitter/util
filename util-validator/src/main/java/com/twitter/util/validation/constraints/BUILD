java_library(
    sources = ["*.java"],
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-validation-java-constraints",
        repo = artifactory,
    ),
    strict_deps = True,
    dependencies = [
        "3rdparty/jvm/jakarta/validation:jakarta.validation-api",
    ],
    exports = [
        "3rdparty/jvm/jakarta/validation:jakarta.validation-api",
    ],
)
