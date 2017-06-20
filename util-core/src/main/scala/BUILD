scala_library(
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-core',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/org/scala-lang/modules:scala-parser-combinators',
    '3rdparty/jvm/org/scala-lang:scala-reflect',
    'util/util-function/src/main/java',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
  strict_deps=True,
)
