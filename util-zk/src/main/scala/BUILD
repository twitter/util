scala_library(name='scala',
  provides = artifact(
    org = 'com.twitter',
    name = 'util-zk',
    repo = pants('pants-support/ivy:internal'),
  ),
  dependencies=[
    pants('3rdparty:zookeeper'),
    pants('util/util-core/src/main/scala'),
    pants('util/util-logging/src/main/scala')
  ],
  sources=rglobs('*.scala'),
)
