scala_library(name='scala',
  provides = artifact(
    org = 'com.twitter',
    name = 'util-codec',
    repo = pants('pants-support/ivy:internal'),
  ),
  dependencies=[
    pants('3rdparty:commons-codec'),
    pants('util/util-core/src/main/scala')
  ],
  sources=rglobs('*.scala'),
)
