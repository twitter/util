resolvers += Classpaths.sbtPluginReleases
resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.4.1")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.0")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.3")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.1")
