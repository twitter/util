scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-test',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty:scalatest',
    '3rdparty:mockito-all',
    'util/util-logging/src/main/scala',
    'util/util-core/src/main/scala',
  ],
  sources=rglobs('*.scala'),
)
