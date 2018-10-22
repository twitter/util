scala_library(
    compiler_option_sets = {"fatal_warnings"},
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-test-testing",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/org/mockito:mockito-all",
        "3rdparty/jvm/org/scalatest",
    ],
    exports = [
        "3rdparty/jvm/org/scalatest",
    ],
)
