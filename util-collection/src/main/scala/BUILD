scala_library(
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-collection',
    repo = artifactory,
  ),
  dependencies=[
    'util/util-core/src/main/scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
  exports=[
    'util/util-core/src/main/scala:scala',
  ]

)
