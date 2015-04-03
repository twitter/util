java_library(name='java',
  provides = artifact(
    org = 'com.twitter',
    name = 'util-cache-java',
    repo = artifactory,
  ),
  sources=rglobs('*.java'),
  dependencies=[
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/org/scala-lang:scala-library',
    'util/util-cache/src/main/scala',
    'util/util-core/src/main/scala'
  ]
)
