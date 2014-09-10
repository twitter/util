scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-app',
    repo = 'pants-support/ivy:internal',
  ),
  dependencies=[
    'util/util-core/src/main/scala',
  ],
  sources=rglobs('*.scala'),
  java_sources=['util/util-app/src/main/java',],
)
