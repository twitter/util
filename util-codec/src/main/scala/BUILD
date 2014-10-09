scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-codec',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty:commons-codec',
    'util/util-core/src/main/scala'
  ],
  sources=rglobs('*.scala'),
)
