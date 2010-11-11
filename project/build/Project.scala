import sbt._
import com.twitter.sbt._

class UtilProject(info: ProjectInfo) extends DefaultProject(info) with SubversionPublisher {
  override def managedStyle = ManagedStyle.Maven
  override def disableCrossPaths = true
  override def subversionRepository = Some("http://svn.local.twitter.com/maven-public")

  val twitterRepo = "twitter.com" at "http://maven.twttr.com"

  val scalaTools = "org.scala-lang" % "scala-compiler" % "2.8.0" % "compile"
  override def filterScalaJars = false

  val guava = "com.google.guava" % "guava" % "r06"
  val commonsCollections = "commons-collections" % "commons-collections" % "3.2.1"

  val mockito = "org.mockito" % "mockito-all" % "1.8.5" % "test" withSources()
  val specs = "org.scala-tools.testing" % "specs_2.8.0" % "1.6.5" % "test" withSources()
  val junit = "junit" % "junit" % "3.8.2" % "test"
  val vscaladoc = "org.scala-tools" % "vscaladoc" % "1.1-md-3" % "provided"

  override def compileOptions = super.compileOptions ++ Seq(Unchecked) ++
    compileOptions("-encoding", "utf8") ++
    compileOptions("-deprecation")
}
