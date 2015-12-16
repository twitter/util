java_library(name='java',
  provides = artifact(
    org = 'com.twitter',
    name = 'util-events-java',
    repo = artifactory,
  ),
  dependencies=[
    'util/util-events/src/main/scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.java'),
)
