scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-zk',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/org/apache/zookeeper:zookeeper-client',
    'util/util-core/src/main/scala',
    'util/util-logging/src/main/scala'
  ],
  sources=rglobs('*.scala'),
)
