scala_library(
  fatal_warnings=True,
  sources=rglobs('*.scala'),
  dependencies=[
    '3rdparty/jvm/com/google/guava',
    'util/util-cache/src/main/scala:scala',
    'util/util-core/src/main/scala:scala',
  ],
  provides=scala_artifact(org='com.twitter',
                          name='util-cache-guava',
                          repo = artifactory,),
)
