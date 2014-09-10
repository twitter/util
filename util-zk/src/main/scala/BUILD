scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-zk',
    repo = 'pants-support/ivy:internal',
  ),
  dependencies=[
    '3rdparty:zookeeper',
    'util/util-core/src/main/scala',
    'util/util-logging/src/main/scala'
  ],
  sources=rglobs('*.scala'),
)
