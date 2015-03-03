scala_library(name='scala',
  dependencies=[
    '3rdparty/jvm/org/openjdk/jmh:jmh-core',
    '3rdparty/jvm/org/scala-lang:scala-library',
    'util/util-core/src/main/scala',
    'util/util-events/src/main/scala',
    'util/util-jvm/src/main/scala'
  ],
  sources=rglobs('*.scala')
)

jvm_binary(name='jmh',
  main='org.openjdk.jmh.Main',
  dependencies=[
    ':scala'
  ]
)
