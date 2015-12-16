scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-logging',
    repo = artifactory,
  ),
  dependencies=[
    'util/util-app/src/main/scala',
    'util/util-core/src/main/scala',
    'util/util-stats/src/main/scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
)
