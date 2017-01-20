scala_library(
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-slf4j-jul-bridge',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/org/slf4j:jul-to-slf4j',
    '3rdparty/jvm/org/slf4j:slf4j-api',
    'util/util-core/src/main/scala:scala',
    'util/util-slf4j-api/src/main/scala:scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
)
