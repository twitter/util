scala_library(name='scala',
  provides = artifact(
    org = 'com.twitter',
    name = 'util-thrift',
    repo = pants('pants-support/ivy:internal'),
  ),
  dependencies=[
    pants('3rdparty:jackson-databind'),
    pants('3rdparty:libthrift-0.5.0'),
    pants('3rdparty:slf4j-api'),
    pants('util/util-codec')
  ],
  sources=rglobs('*.scala'),
)
