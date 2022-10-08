java_library(
    sources = [
        "*.java",
        "constraints/*.java",
    ],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/jakarta/validation:jakarta.validation-api",
    ],
    exports = [
        "3rdparty/jvm/jakarta/validation:jakarta.validation-api",
    ],
)
