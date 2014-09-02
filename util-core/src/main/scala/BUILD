scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-core',
    repo = pants('pants-support/ivy:internal'),
  ),
  sources=rglobs('*.scala'),
  java_sources=[
    pants('util/util-core/src/main/java')
  ],
)
