scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-app',
    repo = pants('pants-support/ivy:internal'),
  ),
  dependencies=[
    pants('util/util-core/src/main/scala'),
  ],
  sources=rglobs('*.scala'),
  java_sources=[pants('util/util-app/src/main/java'),],
)
