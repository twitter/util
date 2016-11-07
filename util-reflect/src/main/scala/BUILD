scala_library(
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-reflect',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/cglib',
    'util/util-core/src/main/scala'
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
)
