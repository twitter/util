scala_library(name='scala',
  provides = artifact(
    org = 'com.twitter',
    name = 'util-core',
    repo = pants('pants-support/ivy:internal'),
  ),
  sources=rglobs('*.scala'),
  java_sources=[
    pants('util/util-core/src/main/java')
  ],
)
