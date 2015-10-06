scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-zk-test',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/org/apache/zookeeper:zookeeper-server',
  ],
  sources=rglobs('*.scala'),
)
