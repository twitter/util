scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-stats',
    repo = artifactory,
  ),
  dependencies=[
    'util/util-core/src/main/scala',
    'util/util-lint/src/main/scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala')
)
