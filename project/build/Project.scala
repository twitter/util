import sbt._

class UtilProject(info: ProjectInfo) extends DefaultProject(info) {
  override def disableCrossPaths = true
  override def managedStyle = ManagedStyle.Maven
  val publishTo = Resolver.sftp("sftp:scala0.net", "scala0.net", "/var/www/scala0.net/public/repositories")
  val scalaTools = "org.scala-lang" % "scala-compiler" % "2.7.7" % "compile"
  override def filterScalaJars = false

  val lagRepo = "lag.net" at "http://www.lag.net/repo/"
  val lagNest = "lag.net/nest" at "http://www.lag.net/nest/"

  val guava = "com.google.guava" % "guava" % "r06"
  val commonsCollections = "commons-collections" % "commons-collections" % "3.2.1"
  val mockito = "org.mockito" % "mockito-core" % "1.8.1" % "test"
  val specs = "org.scala-tools.testing" % "specs" % "1.6.2.1" % "test"
  val junit = "junit" % "junit" % "3.8.2" % "test"
  val vscaladoc = "org.scala-tools" % "vscaladoc" % "1.1-md-3" % "provided"
}
