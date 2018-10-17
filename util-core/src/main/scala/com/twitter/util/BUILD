scala_library(
    sources = globs(
        "*.scala",
        "../concurrent/NamedPoolThreadFactory.scala",
        "../concurrent/Offer.scala",
        "../concurrent/Scheduler.scala",
        "../concurrent/Tx.scala",
    ),
    compiler_option_sets = {"fatal_warnings"},
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-core-util",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/org/scala-lang/modules:scala-parser-combinators",
        "util/util-function/src/main/java",
    ],
    exports = [
        "3rdparty/jvm/org/scala-lang/modules:scala-parser-combinators",
        "util/util-function/src/main/java",
    ],
)
