resolvers += Classpaths.sbtPluginReleases
resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.0")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.8.1")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.0")
