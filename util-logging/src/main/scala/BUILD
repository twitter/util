scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-logging',
    repo = 'pants-support/ivy:internal',
  ),
  dependencies=[
    # TODO(John Sirois): This is a bogus dependency for nearly all use cases - isolate
    # com.twitter.logging.TestLogging to its own scala_library target and move users over.
    '3rdparty:scalatest',
    'util/util-app/src/main/scala',
    'util/util-core/src/main/scala',
  ],
  sources=rglobs('*.scala'),
)
