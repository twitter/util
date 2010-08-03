import sbt._
import com.twitter.sbt._

class UtilProject(info: ProjectInfo) extends StandardProject(info) with SubversionRepository {
  val guava = "com.google.guava" % "guava" % "r06"
  val commonsCollections = "commons-collections" % "commons-collections" % "3.2.1"
  val specs = "org.scala-tools.testing" % "specs" % "1.6.2.1" % "test"
  val mockito = "org.mockito" % "mockito-core" % "1.8.1" % "test"
  val vscaladoc = "org.scala-tools" % "vscaladoc" % "1.1-md-3" % "provided"
}
