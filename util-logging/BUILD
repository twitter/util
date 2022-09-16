target(
    tags = [
        "bazel-compatible",
        "visibility://codestructure/packages/util-logging:legal",
    ],
    dependencies = [
        "util/util-logging/src/main/scala",
    ],
)

test_suite(
    name = "tests",
    tags = ["bazel-compatible"],
    dependencies = [
        "util/util-logging/src/test/java",
        "util/util-logging/src/test/scala",
    ],
)
