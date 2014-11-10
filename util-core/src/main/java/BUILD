java_library(name='java',
  provides = artifact(
    org = 'com.twitter',
    name = 'util-core-java',
    repo = artifactory,
  ),
  sources=rglobs('*.java'),
  dependencies=[
    '3rdparty:scala-library',
    'util/util-core/src/main/scala'
  ]
)
