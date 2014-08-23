scala_library(name='scala',
  provides = artifact(
    org = 'com.twitter',
    name = 'util-reflect',
    repo = pants('pants-support/ivy:internal'),
  ),
  dependencies=[
    pants('3rdparty:asm'),
    pants('3rdparty:asm-commons'),
    pants('3rdparty:asm-util'),
    pants('3rdparty:cglib'),
    pants('util/util-core/src/main/scala')
  ],
  sources=rglobs('*.scala'),
)
