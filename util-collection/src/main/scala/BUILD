scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-collection',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/com/google/guava',
    'util/util-core/src/main/scala',
  ],
  sources=rglobs('*.scala'),
)
