scala_library(
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-collection',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/com/google/guava',
    'util/util-core/src/main/scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
)
