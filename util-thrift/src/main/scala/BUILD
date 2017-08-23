scala_library(
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-thrift',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind',
    '3rdparty/jvm/org/apache/thrift:libthrift-0.5.0',
    'util/util-codec',
    'util/util-core/src/main/scala:scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
  strict_deps=True,
  exports=[
    '3rdparty/jvm/org/apache/thrift:libthrift-0.5.0',
    'util/util-codec',
  ],
)
