scala_library(
    sources = rglobs("*.scala"),
    fatal_warnings = True,
    provides = scala_artifact(
        org = "com.twitter",
        name = "util-core",
        repo = artifactory,
    ),
    dependencies = [
        "3rdparty/jvm/org/scala-lang:scala-reflect",
        "3rdparty/jvm/org/scala-lang/modules:scala-parser-combinators",
        "util/util-function/src/main/java",
    ],
    exports = [
        "3rdparty/jvm/org/scala-lang/modules:scala-parser-combinators",
        "util/util-function/src/main/java",
    ],
)
