scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-tunable',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-core',
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind',
    '3rdparty/jvm/com/fasterxml/jackson/module:jackson-module-scala',
    'util/util-app/src/main/scala',
    'util/util-core/src/main/scala:scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala', exclude=[rglobs('util/tunable/linter/*')]),
  exports=[
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind',
  ]
)
