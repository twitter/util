scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-reflect',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/asm',
    '3rdparty/jvm/asm:asm-commons',
    '3rdparty/jvm/asm:asm-util',
    '3rdparty/jvm/cglib',
    'util/util-core/src/main/scala'
  ],
  sources=rglobs('*.scala'),
)
