scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-jvm',
    repo = 'pants-support/ivy:internal',
  ),
  dependencies=[
    'util/util-app/src/main/scala',
    'util/util-core/src/main/scala'
  ],
  sources=rglobs('*.scala'),
)
