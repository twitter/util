resolvers += Classpaths.sbtPluginReleases
resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.0")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC13")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.4")
