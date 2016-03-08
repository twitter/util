import sbt.Keys._
import sbt._
import pl.project13.scala.sbt.JmhPlugin
import sbtunidoc.Plugin.unidocSettings
import scoverage.ScoverageSbtPlugin

object Util extends Build {
  val branch = Process("git" :: "rev-parse" :: "--abbrev-ref" :: "HEAD" :: Nil).!!.trim
  val suffix = if (branch == "master") "" else "-SNAPSHOT"

  val libVersion = "6.32.0" + suffix
  val zkVersion = "3.5.0-alpha"
  val zkClientVersion = "0.0.79"
  val zkGroupVersion = "0.0.90"
  val zkDependency = "org.apache.zookeeper" % "zookeeper" % zkVersion excludeAll(
    ExclusionRule("com.sun.jdmk", "jmxtools"),
    ExclusionRule("com.sun.jmx", "jmxri"),
    ExclusionRule("javax.jms", "jms")
  )

  val parserCombinators = scalaVersion { sv =>
    CrossVersion.partialVersion(sv) match {
      case Some((2, x)) if x >= 11 =>
        Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4")
      case _ => Nil
    }
  }

  def scalacOptionsVersion(sv: String): Seq[String] = {
    Seq(
      // Note: Add -deprecation when deprecated methods are removed
      "-unchecked",
      "-feature",
      "-encoding", "utf8"
    ) ++ (CrossVersion.partialVersion(sv) match {
      // Needs -missing-interpolator due to https://issues.scala-lang.org/browse/SI-8761
      case Some((2, x)) if x >= 11 => Seq("-Xlint:-missing-interpolator")
      case _ => Seq("-Xlint")
    })
  }

  val sharedSettings = Seq(
    version := libVersion,
    organization := "com.twitter",
    scalaVersion := "2.11.7",
    crossScalaVersions := Seq("2.10.6", "2.11.7"),
    // Workaround for a scaladoc bug which causes it to choke on empty classpaths.
    unmanagedClasspath in Compile += Attributed.blank(new java.io.File("doesnotexist")),
    libraryDependencies ++= Seq(
      "junit" % "junit" % "4.8.1" % "test",
      "org.mockito" % "mockito-all" % "1.9.5" % "test",
      "org.scalatest" %% "scalatest" % "2.2.4" % "test"
    ),

    resolvers += "twitter repo" at "https://maven.twttr.com",

    ScoverageSbtPlugin.ScoverageKeys.coverageHighlighting := (
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 10)) => false
        case _ => true
      }
    ),

    scalacOptions := scalacOptionsVersion(scalaVersion.value),

    // Note: Use -Xlint rather than -Xlint:unchecked when TestThriftStructure
    // warnings are resolved
    javacOptions ++= Seq("-Xlint:unchecked", "-source", "1.7", "-target", "1.7"),
    javacOptions in doc := Seq("-source", "1.7"),

    // This is bad news for things like com.twitter.util.Time
    parallelExecution in Test := false,

    // Sonatype publishing
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    publishMavenStyle := true,
    autoAPIMappings := true,
    apiURL := Some(url("https://twitter.github.io/util/docs/")),
    pomExtra :=
      <url>https://github.com/twitter/util</url>
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0</url>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:twitter/util.git</url>
        <connection>scm:git:git@github.com:twitter/util.git</connection>
      </scm>
      <developers>
        <developer>
          <id>twitter</id>
          <name>Twitter Inc.</name>
          <url>https://www.twitter.com/</url>
        </developer>
      </developers>,
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },

    // Prevent eviction warnings
    dependencyOverrides <++= scalaVersion { vsn =>
      Set(
        "com.twitter.common.zookeeper" % "client" % zkClientVersion,
        "com.twitter.common.zookeeper" % "group"  % zkGroupVersion
      )
    }
  )

  lazy val util = Project(
    id = "util",
    base = file("."),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings ++
      unidocSettings
  ) aggregate(
    utilFunction, utilRegistry, utilCore, utilCodec, utilCollection, utilCache, utilReflect,
    utilLint, utilLogging, utilTest, utilThrift, utilHashing, utilJvm, utilZk,
    utilZkCommon, utilZkTest, utilClassPreloader, utilBenchmark, utilApp,
    utilEvents, utilStats, utilEval
  )

  lazy val utilApp = Project(
    id = "util-app",
    base = file("util-app"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-app"
  ).dependsOn(utilCore, utilRegistry)

  lazy val utilBenchmark = Project(
    id = "util-benchmark",
    base = file("util-benchmark"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings ++ JmhPlugin.projectSettings
  )
  .enablePlugins(JmhPlugin)
  .settings(
    name := "util-benchmark"
  ).dependsOn(utilCore, utilEvents, utilHashing, utilJvm, utilStats)

  lazy val utilCache = Project(
    id = "util-cache",
    base = file("util-cache"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-cache",
    libraryDependencies ++= Seq(
      // NB: guava has a `provided` dep on jsr/javax packages, so we include them manually
      "com.google.code.findbugs" % "jsr305"              % "2.0.1",
      "com.google.guava"         % "guava"               % "16.0.1"
    )
  ).dependsOn(utilCore)

  lazy val utilClassPreloader = Project(
    id = "util-class-preloader",
    base = file("util-class-preloader"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-class-preloader"
  ).dependsOn(utilCore)

  lazy val utilCodec = Project(
    id = "util-codec",
    base = file("util-codec"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-codec",
    libraryDependencies ++= Seq(
      "commons-codec" % "commons-codec" % "1.9"
    )
  ).dependsOn(utilCore)

  lazy val utilCollection = Project(
    id = "util-collection",
    base = file("util-collection"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-collection",
    libraryDependencies ++= Seq(
      // NB: guava has a `provided` dep on jsr/javax packages, so we include them manually
      "com.google.code.findbugs" % "jsr305"              % "2.0.1",
      "javax.inject"             % "javax.inject"        % "1",
      "com.google.guava"         % "guava"               % "16.0.1",
      "commons-collections"      % "commons-collections" % "3.2.2",
      "org.scalacheck"          %% "scalacheck"          % "1.12.2" % "test"
    )
  ).dependsOn(utilCore % "compile->compile;test->test")

  lazy val utilCore = Project(
    id = "util-core",
    base = file("util-core"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-core",
    libraryDependencies ++= Seq(
      "com.twitter" % "jsr166e" % "1.0.0",
      "com.twitter.common" % "objectsize" % "0.0.10" % "test",
      "org.scalacheck" %% "scalacheck" % "1.12.2" % "test"
    ),
    libraryDependencies <++= parserCombinators,
    resourceGenerators in Compile <+=
      (resourceManaged in Compile, name, version) map { (dir, name, ver) =>
        val file = dir / "com" / "twitter" / name / "build.properties"
        val buildRev = Process("git" :: "rev-parse" :: "HEAD" :: Nil).!!.trim
        val buildName = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date)
        val contents = s"name=$name\nversion=$ver\nbuild_revision=$buildRev\nbuild_name=$buildName"
        IO.write(file, contents)
        Seq(file)
      }
  ).dependsOn(utilFunction)

  lazy val utilEval = Project(
    id = "util-eval",
    base = file("util-eval"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-eval",
    libraryDependencies <+= scalaVersion {
      "org.scala-lang" % "scala-compiler" % _ % "compile"
    }
  ).dependsOn(utilCore)

  lazy val utilEvents = Project(
    id = "util-events",
    base = file("util-events"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-events"
  ).dependsOn(utilApp)

  lazy val utilFunction = Project(
    id = "util-function",
    base = file("util-function"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-function"
  )

  lazy val utilReflect = Project(
    id = "util-reflect",
    base = file("util-reflect"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-reflect",
    libraryDependencies ++= Seq(
      "asm"   % "asm"         % "3.3.1",
      "asm"   % "asm-util"    % "3.3.1",
      "asm"   % "asm-commons" % "3.3.1",
      "cglib" % "cglib"       % "2.2.2"
    )
  ).dependsOn(utilCore)

  lazy val utilHashing = Project(
    id = "util-hashing",
    base = file("util-hashing"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-hashing",
    libraryDependencies ++= Seq(
      "commons-codec" % "commons-codec" % "1.9" % "test",
      "org.scalacheck" %% "scalacheck" % "1.12.2" % "test"
    )
  ).dependsOn(utilCore % "test")

  lazy val utilJvm = Project(
    id = "util-jvm",
    base = file("util-jvm"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-jvm"
  ).dependsOn(utilApp, utilCore, utilTest % "test")

  lazy val utilLint = Project(
    id = "util-lint",
    base = file("util-lint"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-lint"
  )

  lazy val utilLogging = Project(
    id = "util-logging",
    base = file("util-logging"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-logging"
  ).dependsOn(utilCore, utilApp, utilStats)


  lazy val utilRegistry = Project(
    id = "util-registry",
    base = file("util-registry"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-registry"
  ).dependsOn(utilCore)

  lazy val utilStats = Project(
    id = "util-stats",
    base = file("util-stats"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-stats"
  ).dependsOn(utilCore, utilLint)

  lazy val utilTest = Project(
    id = "util-test",
    base = file("util-test"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-test",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "2.2.4",
      "org.mockito" % "mockito-all" % "1.9.5"
    )
  ).dependsOn(utilCore, utilLogging)


  lazy val utilThrift = Project(
    id = "util-thrift",
    base = file("util-thrift"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-thrift",
    libraryDependencies ++= Seq(
      "thrift"                     % "libthrift"        % "0.5.0",
      "org.slf4j"                  % "slf4j-api"        % "1.7.7" % "provided",
      "com.fasterxml.jackson.core" % "jackson-core"     % "2.4.4",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4"
    )
  ).dependsOn(utilCodec)

  lazy val utilZk = Project(
    id = "util-zk",
    base = file("util-zk"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-zk",
    libraryDependencies ++= Seq(
      zkDependency
    )
  ).dependsOn(utilCore, utilCollection, utilLogging)

  lazy val utilZkCommon = Project(
    id = "util-zk-common",
    base = file("util-zk-common"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-zk-common",
    libraryDependencies ++= Seq(
      "com.twitter.common.zookeeper" % "client"     % zkClientVersion,
      "com.twitter.common.zookeeper" % "group"      % zkGroupVersion,
      "com.twitter.common.zookeeper" % "server-set" % "1.0.103",
      zkDependency
    )
  ).dependsOn(utilCore, utilLogging, utilZk,
    // These are depended on to provide transitive dependencies
    // that would otherwise cause incompatibilities. See above comment.
    utilCollection, utilHashing
  )

  lazy val utilZkTest = Project(
    id = "util-zk-test",
    base = file("util-zk-test"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-zk-test",
    libraryDependencies ++= Seq(
      "com.twitter.common" % "io" % "0.0.67" % "test",
      zkDependency
    )
  )

}
