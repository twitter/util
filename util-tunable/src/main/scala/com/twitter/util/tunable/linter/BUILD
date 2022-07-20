scala_library(
    name = "scala",
    platform = "java8",
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/com/fasterxml/jackson/core:jackson-core",
        "3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind",
        "3rdparty/jvm/com/fasterxml/jackson/module:jackson-module-scala",
        "util/util-app/src/main/scala",
        "util/util-core:util-core-util",
        "util/util-tunable/src/main/scala/com/twitter/util/tunable",
    ],
)

jvm_binary(
    name = "configuration-linter",
    main = "com.twitter.util.tunable.linter.ConfigurationLinter",
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    tags = ["bazel-compatible"],
    dependencies = [
        ":scala",
    ],
)
