scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-thrift',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind',
    '3rdparty/jvm/org/apache/thrift:libthrift-0.5.0',
    '3rdparty/jvm/org/slf4j:slf4j-api',
    'util/util-codec'
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
)
