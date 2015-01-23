benchmark(name='scala',
  dependencies=[
    '3rdparty/jvm/com/google/caliper',
    '3rdparty/jvm/org/scala-lang:scala-library',
    'util/util-core/src/main/scala',
    'util/util-events/src/main/scala',
    'util/util-jvm/src/main/scala'
  ],
  sources=rglobs('*.scala')
)

jvm_binary(name='caliper',
  main='com.google.caliper.Runner',
  dependencies=[
    ':scala'
  ]
)
