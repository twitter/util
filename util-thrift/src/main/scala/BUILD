scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-thrift',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind',
    '3rdparty:libthrift-0.5.0',
    '3rdparty:slf4j-api',
    'util/util-codec'
  ],
  sources=rglobs('*.scala'),
)
