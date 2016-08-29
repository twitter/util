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
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
)
