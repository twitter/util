scala_library(
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-slf4j',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/org/slf4j:slf4j-api',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
  strict_deps=True,
)
