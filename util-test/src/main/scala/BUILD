scala_library(
    # This target exists only to act as an aggregator jar.
    sources = ["PantsWorkaround.scala"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-test",
        repo = artifactory,
    ),
    dependencies = [
        "util/util-test/src/main/scala/com/twitter/logging",
        "util/util-test/src/main/scala/com/twitter/util/testing",
    ],
    exports = [
        "3rdparty/jvm/org/scalatest",
        "util/util-test/src/main/scala/com/twitter/logging",
        "util/util-test/src/main/scala/com/twitter/util/testing",
    ],
)
