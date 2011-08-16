import sbt._
import com.twitter.sbt._

class Project(info: ProjectInfo)
  extends StandardParentProject(info)
  with SubversionPublisher
  with ParentProjectDependencies
  with DefaultRepos
{
  override def subversionRepository = Some("http://svn.local.twitter.com/maven-public")
  val twitterRepo = "twitter.com" at "http://maven.twttr.com"

  // Projects

  // util-core: extensions with no external dependency requirements
  val coreProject = project(
    "util-core", "util-core",
    new CoreProject(_))

  val evalProject = project(
    "util-eval", "util-eval",
    new EvalProject(_), coreProject)

  val codecProject = project(
    "util-codec", "util-codec",
    new CodecProject(_), coreProject)

  val collectionProject = project(
    "util-collection", "util-collection",
    new CollectionProject(_), coreProject)

  // util-reflect: runtime reflection and dynamic helpers
  val reflectProject = project(
    "util-reflect", "util-reflect",
    new ReflectProject(_), coreProject)

  // util-logging: logging wrappers and configuration
  val loggingProject = project(
    "util-logging", "util-logging",
    new LoggingProject(_), coreProject)

  // util-thrift: thrift (serialization) utilities
  val thriftProject = project(
    "util-thrift", "util-thrift",
    new ThriftProject(_), coreProject, codecProject)

  // util-hashing: hashing and distribution utilities
  val hashingProject = project(
    "util-hashing", "util-hashing",
    new HashingProject(_), coreProject)


  class CoreProject(info: ProjectInfo)
    extends StandardProject(info)
    with ProjectDefaults

  class EvalProject(info: ProjectInfo)
    extends StandardProject(info)
    with ProjectDefaults
  {
    val scalaTools = "org.scala-lang" % "scala-compiler" % "2.8.1" % "compile"
    override def filterScalaJars = false
  }

  class CodecProject(info: ProjectInfo)
    extends StandardProject(info)
    with ProjectDefaults
  {
    val commonsCodec = "commons-codec" % "commons-codec" % "1.5"
  }

  class CollectionProject(info: ProjectInfo)
    extends StandardProject(info)
    with ProjectDefaults
  {
    val guava              = "com.google.guava"    % "guava"               % "r09"
    val commonsCollections = "commons-collections" % "commons-collections" % "3.2.1"
  }

  class ReflectProject(info: ProjectInfo)
    extends StandardProject(info)
    with ProjectDefaults
  {
    val cglib = "cglib" % "cglib" % "2.2"
  }

  class LoggingProject(info: ProjectInfo)
    extends StandardProject(info)
    with ProjectDefaults
  {
    val compileWithSpecs = "org.scala-tools.testing" % "specs_2.8.0" % "1.6.5" % "provided"
  }

  class ThriftProject(info: ProjectInfo)
    extends StandardProject(info)
    with ProjectDefaults
  {
    override def compileOrder = CompileOrder.JavaThenScala
    val thrift = "thrift"        % "libthrift"     % "0.5.0"
    val slf4j  = "org.slf4j"     % "slf4j-nop"     % "1.5.8" % "provided"
  }

  class HashingProject(info: ProjectInfo)
    extends StandardProject(info)
    with ProjectDefaults
  {
      val commonsCodec = "commons-codec" % "commons-codec" % "1.5" % "test"
  }

  trait ProjectDefaults
    extends StandardProject
    with SubversionPublisher
    with PublishSite
    with ProjectDependencies
    with DefaultRepos
  {
    val specs   = "org.scala-tools.testing" % "specs_2.8.0" % "1.6.5" % "test" withSources()
    val mockito = "org.mockito"             % "mockito-all" % "1.8.5" % "test" withSources()
    val junit   = "junit"                   %       "junit" % "3.8.2" % "test"

    override def compileOptions = super.compileOptions ++ Seq(Unchecked) ++
      compileOptions("-encoding", "utf8") ++
      compileOptions("-deprecation")
  }
}
