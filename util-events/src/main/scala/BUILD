scala_library(
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-events',
    repo = artifactory,
  ),
  dependencies=[
    'util/util-app/src/main/scala',
    'util/util-core/src/main/scala:scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
)
