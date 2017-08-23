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
  exports=[
    '3rdparty/jvm/cglib',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
  strict_deps=True,
)
