scala_library(name='scala',
  fatal_warnings=True,
  strict_deps=True,
  sources=rglobs('*.scala'),
  dependencies=[
    '3rdparty/jvm/com/github/ben-manes/caffeine',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
    '3rdparty/jvm/com/google/guava',
    'util/util-core/src/main/scala:scala',
  ],
  provides=scala_artifact(org='com.twitter',
                          name='util-cache',
                          repo = artifactory,),
)
