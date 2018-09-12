java_library(
    sources = rglobs("*.java"),
    compiler_option_sets = {"fatal_warnings"},
    provides = artifact(
        org = "com.twitter",
        name = "util-app-java",
        repo = artifactory,
    ),
    # Not detected as required by zinc.
    scope = "forced",
)
