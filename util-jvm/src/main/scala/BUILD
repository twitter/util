scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-jvm',
    repo = pants('pants-support/ivy:internal'),
  ),
  dependencies=[
    pants('util/util-app/src/main/scala'),
    pants('util/util-core/src/main/scala')
  ],
  sources=rglobs('*.scala'),
)
