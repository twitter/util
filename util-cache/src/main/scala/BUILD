scala_library(name='scala',
  sources=rglobs('*.scala'),
  dependencies=[
    '3rdparty/jvm/com/google/guava',
    'util/util-core'
  ],
  provides=scala_artifact(org='com.twitter',
                          name='util-cache',
                          repo = artifactory,),
)
