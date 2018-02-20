scala_library(
    name = "scala",
    sources = rglobs("*.scala"),
    dependencies = [
        "3rdparty/jvm/com/fasterxml/jackson/core:jackson-core",
        "3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind",
        "3rdparty/jvm/com/fasterxml/jackson/module:jackson-module-scala",
        "util/util-app/src/main/scala",
        "util/util-core/src/main/scala",
        "util/util-tunable/src/main/scala",
    ],
)

jvm_binary(
    name = "configuration-linter",
    main = "com.twitter.util.tunable.linter.ConfigurationLinter",
    fatal_warnings = True,
    dependencies = [
        ":scala",
    ],
)
