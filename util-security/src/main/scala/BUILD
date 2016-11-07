scala_library(
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-security',
    repo = artifactory,
  ),
  fatal_warnings = True,
  strict_deps = True,
  dependencies = [
    'util/util-logging',
    'util/util-core/src/main/scala:scala',
  ],
  sources = rglobs('*.scala')
)
