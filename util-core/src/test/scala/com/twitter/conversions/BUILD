junit_tests(
    sources = ["*.scala"],
    # fatal_warnings runs into an issue with scalatest/Future.isDone:
    #
    # [warn] ...: possible missing interpolator: detected interpolated identifier `$conforms`
    # [warn]     assert(future.close().isDone)
    # [warn]           ^
    #
    # At some point later, when we can enable `-Xlint:-warn-missing-interpolator`
    # for this target, we could try enabling this again.
    compiler_option_sets = {},
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/scalacheck",
        "3rdparty/jvm/org/scalatest",
        "util/util-core/src/main/scala/com/twitter/conversions",
    ],
)
