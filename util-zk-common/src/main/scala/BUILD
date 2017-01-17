scala_library(
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-zk-common',
    repo = artifactory,
  ),
  dependencies=[
    'src/java/com/twitter/common/zookeeper:client',
    'src/java/com/twitter/common/quantity:quantity',
    'util/util-core/src/main/scala',
    'util/util-zk/src/main/scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
)
