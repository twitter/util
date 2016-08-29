java_library(name='java',
  provides = artifact(
    org = 'com.twitter',
    name = 'util-app-java',
    repo = artifactory,
  ),
  fatal_warnings=True,
  sources=rglobs('*.java'),
  # Not detected as required by zinc.
  scope='forced',
)
