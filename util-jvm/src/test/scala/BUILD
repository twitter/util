junit_tests(
  dependencies=[
    '3rdparty/jvm/junit',
    '3rdparty/jvm/org/mockito:mockito-all',
    '3rdparty/jvm/org/scalatest',
    'util/util-app/src/main/scala:scala',
    'util/util-core/src/main/scala',
    'util/util-jvm/src/main/scala',
    'util/util-logging/src/main/scala',
    'util/util-test/src/main/scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
  strict_deps=True,

)

jvm_binary(name='bin',
  main='com.twitter.jvm.EstimatorApp',
  dependencies=[
    ':scala'
  ]
)
