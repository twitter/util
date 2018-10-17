scala_library(
    sources = globs(
        "*.scala",
        exclude = [
            # the com/twitter/util dependency already includes these files
            globs(
                "NamedPoolThreadFactory.scala",
                "Offer.scala",
                "Scheduler.scala",
                "Tx.scala",
            ),
        ],
    ),
    compiler_option_sets = {"fatal_warnings"},
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-core-concurrent",
        repo = artifactory,
    ),
    dependencies = [
        "util/util-core/src/main/scala/com/twitter/util",
    ],
)
