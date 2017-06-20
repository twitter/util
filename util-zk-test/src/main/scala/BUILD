scala_library(
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-zk-test',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/org/apache/zookeeper:zookeeper-server',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
  strict_deps=True,
)
