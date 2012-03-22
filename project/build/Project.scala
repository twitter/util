import sbt._
import com.twitter.sbt._
import Configurations._

class Project(info: ProjectInfo)
  extends StandardParentProject(info)
  with SubversionPublisher
  with ParentProjectDependencies
  with DefaultRepos
{
  override def usesMavenStyleBasePatternInPublishLocalConfiguration = true
  override def subversionRepository = Some("https://svn.twitter.biz/maven-public")
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

  // util-jvm: jvm (mostly hotspot) utilities
  val jvmProject = project(
    "util-jvm", "util-jvm",
    new JvmProject(_), coreProject)

  // util-zk: An asynchronous ZooKeeper client
  val zkProject = project(
    "util-zk", "util-zk",
    new ZkProject(_), coreProject, collectionProject, loggingProject)

  // util-zk-common: Uses common-zookeeper for connection management
  val zkCommonProject = project(
    "util-zk-common", "util-zk-common",
    new ZkCommonProject(_), coreProject, loggingProject, zkProject,
    // These are required to provide transitive dependencies that
    // would otherwise be incompatible, see ZkCommonProject for more
    // details.
    evalProject, collectionProject, hashingProject
    )

  class CoreProject(info: ProjectInfo)
    extends StandardProject(info)
    with Mockito
    with ProjectDefaults
  {
    override def compileOrder = CompileOrder.Mixed
    val objectSize = "com.twitter.common" % "objectsize" % "0.0.3" % "test"
  }

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
    with Mockito
  {
    val guava              = "com.google.guava"    % "guava"               % "11.0.2"
    val commonsCollections = "commons-collections" % "commons-collections" % "3.2.1"
  }

  class ReflectProject(info: ProjectInfo)
    extends StandardProject(info)
    with ProjectDefaults
  {
    val asm = "asm" % "asm" % "3.3.1"
    val asmUtil = "asm" % "asm-util" % "3.3.1"
    val asmCommons = "asm" % "asm-commons" % "3.3.1"
    val cglib = "cglib" % "cglib" % "2.2"
  }

  class LoggingProject(info: ProjectInfo)
    extends StandardProject(info)
    with ProjectDefaults
  {
    val compileWithSpecs = "org.scala-tools.testing" % "specs_2.8.1" % "1.6.8" % "provided"
  }

  class ThriftProject(info: ProjectInfo)
    extends StandardProject(info)
    with ProjectDefaults
  {
    override def compileOrder = CompileOrder.JavaThenScala
    val thrift = "thrift"        % "libthrift"     % "0.5.0"
    val slf4j  = "org.slf4j"     % "slf4j-nop"     % "1.5.8" % "provided"
    val jacksonCore     = "org.codehaus.jackson" % "jackson-core-asl"   % "1.8.1"
    val jacksonMapper   = "org.codehaus.jackson" % "jackson-mapper-asl" % "1.8.1"
  }

  class HashingProject(info: ProjectInfo)
    extends StandardProject(info)
    with ProjectDefaults
    with Mockito
  {
    val commonsCodec = "commons-codec" % "commons-codec" % "1.5" % "test"
  }

  class JvmProject(info: ProjectInfo)
    extends StandardProject(info)
    with ProjectDefaults

  class ZkProject(info: ProjectInfo)
    extends StandardProject(info)
    with ProjectDefaults
    with JMock
  {
    val zookeeper = "org.apache.zookeeper" % "zookeeper" % zkVersion
  }

  class ZkCommonProject(info: ProjectInfo)
    extends StandardProject(info)
    with ProjectDefaults
    with JMock
  {
    val commonClient    = "com.twitter.common.zookeeper" % "client"     % "0.0.6"
    val commonGroup     = "com.twitter.common.zookeeper" % "group"      % "0.0.5"
    val commonServerSet = "com.twitter.common.zookeeper" % "server-set" % "0.0.5"
    val zookeeper = "org.apache.zookeeper" % "zookeeper" % zkVersion
    // com.twitter.common.zookeeper transitively depends on
    // com.twitter.util via com.twitter.finagle. This is otherwise
    // handled by ProjectDependencies because it transitively inlines
    // projects. However, for publishing we need to build without
    // ProjectDependencies turned on. This anyway highlights a
    // dangerous circularity: if this path involves ABI breaking
    // changes, we must publish broken builds.
    override def ivyXML =
      <dependencies>
        <exclude module="util-core"/>
        <exclude module="util-collection"/>
        <exclude module="util-hashing"/>
        <exclude module="util-reflect"/>
        <exclude module="util-thrift"/>
        <exclude module="util-logging"/>
        <exclude module="util-eval"/>
      </dependencies>
 }

  trait ProjectDefaults
    extends StandardProject
    with SubversionPublisher
    with PublishSite
    with ProjectDependencies
    with DefaultRepos
  {
    val specs = "org.scala-tools.testing" % "specs_2.8.1" % "1.6.8" % "test" withSources()
    val junit = "junit"                   %       "junit" % "3.8.2" % "test"

    val zkVersion = "3.3.4"
    override def ivyXML =
      <dependencies>
        <exclude org="com.sun.jmx" module="jmxri" />
        <exclude org="com.sun.jdmk" module="jmxtools" />
        <exclude org="javax.jms" module="jms" />
      </dependencies>

    override def compileOptions = super.compileOptions ++ Seq(Unchecked) ++
      compileOptions("-encoding", "utf8") ++
      compileOptions("-deprecation")
  }

  trait Mockito extends StandardProject {
    val mockito = "org.mockito" % "mockito-all" % "1.8.5" % "test" withSources()
  }

  trait JMock extends StandardProject {
    val jmock = "org.jmock" % "jmock" % "2.4.0" % "test"
    val cglib = "cglib" % "cglib" % "2.1_3" % "test"
    val asm = "asm" % "asm" % "1.5.3" % "test"
    val objenesis = "org.objenesis" % "objenesis" % "1.1" % "test"
    val hamcrest = "org.hamcrest" % "hamcrest-all" % "1.1" % "test"
  }
}
