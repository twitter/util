java_library(
  provides = artifact(
    org = 'com.twitter',
    name = 'util-core-java',
    repo = artifactory,
  ),
  fatal_warnings=True,
  sources=rglobs('*.java'),
  dependencies=[
    '3rdparty/jvm/org/scala-lang:scala-library',
    'util/util-core/src/main/scala'
  ],
  exports=[
    '3rdparty/jvm/org/scala-lang:scala-library',
    'util/util-core/src/main/scala:scala',
  ]
)
