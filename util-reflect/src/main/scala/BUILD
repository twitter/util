scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-reflect',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty:asm',
    '3rdparty:asm-commons',
    '3rdparty:asm-util',
    '3rdparty:cglib',
    'util/util-core/src/main/scala'
  ],
  sources=rglobs('*.scala'),
)
