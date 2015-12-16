scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-events',
    repo = artifactory,
  ),
  dependencies=[
    'util/util-app/src/main/scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
)
