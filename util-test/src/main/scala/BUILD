scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-test',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/org/mockito:mockito-all',
    '3rdparty/jvm/org/scalatest',
    'util/util-logging/src/main/scala',
    'util/util-core/src/main/scala',
  ],
  sources=rglobs('*.scala'),
)
