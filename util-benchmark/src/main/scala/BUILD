benchmark(name='scala',
  dependencies=[
    '3rdparty:caliper',
    '3rdparty:scala-library',
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
