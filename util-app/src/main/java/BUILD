java_library(
    sources = rglobs("*.java"),
    fatal_warnings = True,
    provides = artifact(
        org = "com.twitter",
        name = "util-app-java",
        repo = artifactory,
    ),
    # Not detected as required by zinc.
    scope = "forced",
)
