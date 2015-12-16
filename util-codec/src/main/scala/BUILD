scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-codec',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/commons-codec',
    'util/util-core/src/main/scala'
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
)
