scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-eval',
    repo = 'pants-support/ivy:internal',
  ),
  dependencies=[
    '3rdparty:scala-compiler',
    'util/util-core/src/main/scala'
  ],
  sources=rglobs('*.scala'),
)
