scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-eval',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty:scala-compiler',
    'util/util-core/src/main/scala'
  ],
  sources=rglobs('*.scala'),
)
