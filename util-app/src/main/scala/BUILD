scala_library(
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
  strict_deps=True,
  exports=[
    'util/util-app/src/main/java:java',
    'util/util-core/src/main/scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
)
