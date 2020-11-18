junit_tests(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    tags = ["non-exclusive"],
    dependencies = [
        "3rdparty/jvm/com/fasterxml/jackson/core:jackson-core",
        "3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind",
        "3rdparty/jvm/com/fasterxml/jackson/module:jackson-module-scala",
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/scalacheck",
        "3rdparty/jvm/org/scalatest",
        "3rdparty/jvm/org/scalatestplus:junit",
        "util/util-core:util-core-util",
        "util/util-core/src/main/scala/com/twitter/conversions",
        "util/util-tunable/src/main/scala/com/twitter/util/tunable",
        "util/util-tunable/src/test/resources",
    ],
)
