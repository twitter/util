scala_library(name = 'scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'util-security',
    repo = artifactory,
  ),
  fatal_warnings = True,
  strict_deps = True,
  dependencies = [
    'util/util-core',
    'util/util-logging'
  ],
  sources = rglobs('*.scala')
)
