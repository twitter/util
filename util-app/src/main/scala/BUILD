scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-app',
    repo = artifactory,
  ),
  dependencies=[
    'util/util-app/src/main/java',
    'util/util-core/src/main/scala',
    'util/util-registry/src/main/scala',
  ],
  sources=rglobs('*.scala'),
)
