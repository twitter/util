scala_library(name='scala',
  dependencies=[
    # TODO(John Sirois): This is a bogus dependency for nearly all use cases - isolate
    # com.twitter.logging.TestLogging to its own scala_library target and move users over.
    pants('3rdparty:scalatest'),
    pants('util/util-app/src/main/scala'),
    pants('util/util-core/src/main/scala'),
  ],
  sources=rglobs('*.scala'),
  provides=artifact(org='com.twitter',
                    name='util-logging',
                    repo='pants-support/ivy:internal',),
)
