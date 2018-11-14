scala_library(
    name = "scala",
    dependencies = [
        "3rdparty/jvm/com/fasterxml/jackson/core:jackson-core",
        "3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind",
        "3rdparty/jvm/com/fasterxml/jackson/module:jackson-module-scala",
        "util/util-app/src/main/scala",
        "util/util-core/src/main/scala/com/twitter/util",
        "util/util-tunable/src/main/scala/com/twitter/util/tunable",
    ],
)

jvm_binary(
    name = "configuration-linter",
    main = "com.twitter.util.tunable.linter.ConfigurationLinter",
    compiler_option_sets = {"fatal_warnings"},
    dependencies = [
        ":scala",
    ],
)
