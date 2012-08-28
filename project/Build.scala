import sbt._
import Keys._
import com.twitter.sbt._

object Util extends Build {
  val zkVersion = "3.3.4"

  val sharedSettings = Seq(
    version := "5.3.7",
    organization := "com.twitter",
    // Workaround for a scaladoc bug which causes it to choke on
    // empty classpaths.
    unmanagedClasspath in Compile += Attributed.blank(new java.io.File("doesnotexist")),
    SubversionPublisher.subversionRepository := Some("https://svn.twitter.biz/maven-public"),
    libraryDependencies ++= Seq(
      "org.scala-tools.testing" %% "specs" % "1.6.9" % "test" withSources(),
      "junit" % "junit" % "4.8.1" % "test" withSources(),
      "org.mockito" % "mockito-all" % "1.8.5" % "test" withSources()
    ),

    ivyXML :=
      <dependencies>
        <exclude org="com.sun.jmx" module="jmxri" />
        <exclude org="com.sun.jdmk" module="jmxtools" />
        <exclude org="javax.jms" module="jms" />
      </dependencies>,

    scalacOptions ++= Seq("-encoding", "utf8"),
    scalacOptions += "-deprecation",

    // This is bad news for things like com.twitter.util.Time
    parallelExecution in Test := false
  )

  val jmockSettings = Seq(
    libraryDependencies ++= Seq(
      "org.jmock" % "jmock" % "2.4.0" % "test",
      "cglib" % "cglib" % "2.1_3" % "test",
      "asm" % "asm" % "1.5.3" % "test",
      "org.objenesis" % "objenesis" % "1.1" % "test",
      "org.hamcrest" % "hamcrest-all" % "1.1" % "test"
    )
  )

  lazy val util = Project(
    id = "util",
    base = file(".")
  ) aggregate(
    utilCore, utilEval, utilCodec, utilCollection, utilReflect,
    utilLogging, utilThrift, utilHashing, utilJvm, utilZk,
    utilZkCommon, utilClassPreloader, utilBenchmark
  )

  lazy val utilCore = Project(
    id = "util-core",
    base = file("util-core"),
    settings = Project.defaultSettings ++
      StandardProject.newSettings ++
      sharedSettings
  ).settings(
    name := "util-core",
    libraryDependencies ++= Seq(
      "com.twitter.common" % "objectsize" % "0.0.3" % "test"
    )
  )

  lazy val utilEval = Project(
    id = "util-eval",
    base = file("util-eval"),
    settings = Project.defaultSettings ++
      StandardProject.newSettings ++
      sharedSettings
  ).settings(
    name := "util-eval",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % "2.9.1" % "compile"
    )
  ).dependsOn(utilCore)

  lazy val utilCodec = Project(
    id = "util-codec",
    base = file("util-codec"),
    settings = Project.defaultSettings ++
      StandardProject.newSettings ++
      sharedSettings
  ).settings(
    name := "util-codec",
    libraryDependencies ++= Seq(
      "commons-codec" % "commons-codec" % "1.5"
    )
  ).dependsOn(utilCore)

  lazy val utilCollection = Project(
    id = "util-collection",
    base = file("util-collection"),
    settings = Project.defaultSettings ++
      StandardProject.newSettings ++
      sharedSettings
  ).settings(
    name := "util-collection",
    libraryDependencies ++= Seq(
      "com.google.guava" % "guava" % "11.0.2",
      "commons-collections" % "commons-collections" % "3.2.1"
    )
  ).dependsOn(utilCore)

  lazy val utilReflect = Project(
    id = "util-reflect",
    base = file("util-reflect"),
    settings = Project.defaultSettings ++
      StandardProject.newSettings ++
      sharedSettings
  ).settings(
    name := "util-reflect",
    libraryDependencies ++= Seq(
      "asm" % "asm" % "3.3.1",
      "asm" % "asm-util" % "3.3.1",
      "asm" % "asm-commons" % "3.3.1",
      "cglib" % "cglib" % "2.2"
    )
  ).dependsOn(utilCore)

  lazy val utilLogging = Project(
    id = "util-logging",
    base = file("util-logging"),
    settings = Project.defaultSettings ++
      StandardProject.newSettings ++
      sharedSettings
  ).settings(
    name := "util-logging",
    libraryDependencies ++= Seq(
      "org.scala-tools.testing" %% "specs" % "1.6.9" % "provided"
    )
  ).dependsOn(utilCore)

  lazy val utilThrift = Project(
    id = "util-thrift",
    base = file("util-thrift"),
    settings = Project.defaultSettings ++
      StandardProject.newSettings ++
      sharedSettings
  ).settings(
    name := "util-thrift",
    libraryDependencies ++= Seq(
      "thrift" % "libthrift" % "0.5.0",
      "org.slf4j" % "slf4j-nop" % "1.5.8" % "provided",
      "org.codehaus.jackson" % "jackson-core-asl"   % "1.8.1",
      "org.codehaus.jackson" % "jackson-mapper-asl" % "1.8.1"
    )
  ).dependsOn(utilCore, utilCodec)

  lazy val utilHashing = Project(
    id = "util-hashing",
    base = file("util-hashing"),
    settings = Project.defaultSettings ++
      StandardProject.newSettings ++
      sharedSettings
  ).settings(
    name := "util-hashing",
    libraryDependencies ++= Seq(
      "commons-codec" % "commons-codec" % "1.5" % "test"
    )
  ).dependsOn(utilCore)

  lazy val utilJvm = Project(
    id = "util-jvm",
    base = file("util-jvm"),
    settings = Project.defaultSettings ++
      StandardProject.newSettings ++
      sharedSettings
  ).settings(
    name := "util-jvm"
  ).dependsOn(utilCore, utilLogging % "test")

  lazy val utilZk = Project(
    id = "util-zk",
    base = file("util-zk"),
    settings = Project.defaultSettings ++
      StandardProject.newSettings ++
      sharedSettings ++
      jmockSettings
  ).settings(
    name := "util-zk",
    ivyXML :=
      <dependencies>
        <exclude org="com.sun.jmx" module="jmxri" />
        <exclude org="com.sun.jdmk" module="jmxtools" />
        <exclude org="javax.jms" module="jms" />
        <override org="junit" rev="4.8.1"/>
      </dependencies>,
    libraryDependencies ++= Seq(
      "org.apache.zookeeper" % "zookeeper" % zkVersion
    )
  ).dependsOn(utilCore, utilCollection, utilLogging)

  lazy val utilZkCommon = Project(
    id = "util-zk-common",
    base = file("util-zk-common"),
    settings = Project.defaultSettings ++
      StandardProject.newSettings ++
      sharedSettings ++
      jmockSettings
  ).settings(
    name := "util-zk-common",
    libraryDependencies ++= Seq(
      "com.twitter.common.zookeeper" % "client"     % "0.0.10",
      "com.twitter.common.zookeeper" % "group"      % "0.0.14",
      "com.twitter.common.zookeeper" % "server-set" % "1.0.3",
      "org.apache.zookeeper" % "zookeeper" % zkVersion
    )
  ).dependsOn(utilCore, utilLogging, utilZk,
    // These are dependended on to provide transitive dependencies
    // that would otherwise cause incompatibilities. See above comment.
    utilEval, utilCollection, utilHashing
  )

  lazy val utilClassPreloader = Project(
    id = "util-class-preloader",
    base = file("util-class-preloader"),
    settings = Project.defaultSettings ++
      StandardProject.newSettings ++
      sharedSettings
  ).settings(
    name := "util-class-preloader"
  ).dependsOn(utilCore)

  lazy val utilBenchmark = Project(
    id = "util-benchmark",
    base = file("util-benchmark"),
    settings = Project.defaultSettings ++
      StandardProject.newSettings ++
      sharedSettings
  ).settings(
    name := "util-benchmark",
    libraryDependencies ++= Seq(
      "com.google.caliper" % "caliper" % "0.5-rc1"
    )
  ).dependsOn(utilCore, utilJvm)

}
