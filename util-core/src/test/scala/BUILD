junit_tests(name='scala',
  dependencies=[
    'util/util-core/src/test:base',
    'util/util-core/src/test/java',
  ],
  # fatal_warnings runs into an issue with scalatest/Future.isDone:
  #
  # [warn] ...: possible missing interpolator: detected interpolated identifier `$conforms`
  # [warn]     assert(future.close().isDone)
  # [warn]           ^
  #
  # At some point later, when we can enable `-Xlint:-warn-missing-interpolator`
  # for this target, we could try enabling this again.
  fatal_warnings=False,
  sources=rglobs('*.scala')
)
