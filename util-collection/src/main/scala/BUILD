scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-collection',
    repo = pants('pants-support/ivy:internal'),
  ),
  dependencies=[
    pants('3rdparty:commons-collections'),
    pants('3rdparty:guava'),
    pants('util/util-core/src/main/scala'),
  ],
  sources=rglobs('*.scala'),
)
