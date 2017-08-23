scala_library(
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-security',
    repo = artifactory,
  ),
  fatal_warnings = True,
  strict_deps = True,
  dependencies = [
    'util/util-core/src/main/scala:scala',
    'util/util-logging',
  ],
  sources = rglobs('*.scala')
)
