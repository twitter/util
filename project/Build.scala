import sbt.Keys._
import sbt._
import com.typesafe.sbt.SbtSite.site
import com.typesafe.sbt.site.SphinxSupport.Sphinx
import pl.project13.scala.sbt.JmhPlugin
import sbtunidoc.Plugin.unidocSettings
import scoverage.ScoverageSbtPlugin

object Util extends Build {
  val branch = Process("git" :: "rev-parse" :: "--abbrev-ref" :: "HEAD" :: Nil).!!.trim
  val suffix = if (branch == "master") "" else "-SNAPSHOT"

  val libVersion = "6.38.0" + suffix
  val zkVersion = "3.5.0-alpha"
  val zkClientVersion = "0.0.80"
  val zkGroupVersion = "0.0.91"
  val zkDependency = "org.apache.zookeeper" % "zookeeper" % zkVersion excludeAll(
    ExclusionRule("com.sun.jdmk", "jmxtools"),
    ExclusionRule("com.sun.jmx", "jmxri"),
    ExclusionRule("javax.jms", "jms")
  )

  val guavaLib = "com.google.guava" % "guava" % "16.0.1"
  val caffeineLib = "com.github.ben-manes.caffeine" % "caffeine" % "2.3.3"
  val jsr305Lib = "com.google.code.findbugs" % "jsr305" % "2.0.1"
  val scalacheckLib = "org.scalacheck" %% "scalacheck" % "1.12.5" % "test"

  // for various reasons, we use poms that point to the wrong
  // versions of certain libraries.
  def commonsZookeeper(name: String, version: String) =
    "com.twitter.common.zookeeper" % name % version excludeAll(
      ExclusionRule("com.twitter", "finagle-core-java"),
      ExclusionRule("com.twitter", "finagle-core_2.11"),
      ExclusionRule("com.twitter", "util-core-java"),
      ExclusionRule("com.twitter", "util-core_2.11"),
      ExclusionRule("com.twitter.common", "service-thrift"),
      ExclusionRule("org.apache.thrift", "libthrift"),
      ExclusionRule("org.apache.zookeeper", "zookeeper"),
      ExclusionRule("org.apache.zookeeper", "zookeeper-client"),
      ExclusionRule("org.scala-lang.modules", "scala-parser-combinators_2.11")
    )

  val sharedSettings = Seq(
    version := libVersion,
    organization := "com.twitter",
    scalaVersion := "2.11.8",
    crossScalaVersions := Seq("2.11.8", "2.12.0-M4"),
    // Workaround for a scaladoc bug which causes it to choke on empty classpaths.
    unmanagedClasspath in Compile += Attributed.blank(new java.io.File("doesnotexist")),
    libraryDependencies ++= Seq(
      "junit" % "junit" % "4.8.1" % "test",
      "org.mockito" % "mockito-all" % "1.10.19" % "test",
      "org.scalatest" %% "scalatest" % "2.2.6" % "test"
    ),

    ScoverageSbtPlugin.ScoverageKeys.coverageHighlighting := true,

    scalacOptions := Seq(
      // Note: Add -deprecation when deprecated methods are removed
      "-target:jvm-1.8",
      "-unchecked",
      "-feature",
      "-encoding", "utf8",
      // Needs -missing-interpolator due to https://issues.scala-lang.org/browse/SI-8761
      "-Xlint:-missing-interpolator"
    ),

    // scoverage automatically brings in libraries on our behalf, but it hasn't
    // been updated for 2.12 yet[0].  for now, we need to rely on the 2.11 ones
    // (which seem to work OK)
    //
    // [0]: https://github.com/scoverage/sbt-scoverage/issues/126
    libraryDependencies := {
      libraryDependencies.value.map {
        case moduleId: ModuleID
          if moduleId.organization == "org.scoverage"
            && scalaVersion.value.startsWith("2.12") =>
            moduleId.copy(name = moduleId.name.replace(scalaVersion.value, "2.11"))
        case moduleId =>
          moduleId
      }
    },

    // Note: Use -Xlint rather than -Xlint:unchecked when TestThriftStructure
    // warnings are resolved
    javacOptions ++= Seq("-Xlint:unchecked", "-source", "1.8", "-target", "1.8"),
    javacOptions in doc := Seq("-source", "1.8"),

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
        commonsZookeeper("client", zkClientVersion),
        commonsZookeeper("group", zkGroupVersion)
      )
    }
  )

  lazy val util = Project(
    id = "util",
    base = file("."),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings ++
      unidocSettings
  ).aggregate(
    utilFunction, utilRegistry, utilCore, utilCodec, utilCollection, utilCache, utilDoc, utilReflect,
    utilLint, utilLogging, utilTest, utilThrift, utilHashing, utilJvm, utilZk,
    utilZkCommon, utilZkTest, utilClassPreloader, utilBenchmark, utilApp,
    utilEvents, utilSecurity, utilStats, utilEval
  )

  lazy val utilApp = Project(
    id = "util-app",
    base = file("util-app"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-app",
    libraryDependencies ++= Seq(
      guavaLib)
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
    libraryDependencies ++= Seq(guavaLib, caffeineLib, jsr305Lib)
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
    name := "util-codec"
  ).dependsOn(utilCore)

  lazy val utilCollection = Project(
    id = "util-collection",
    base = file("util-collection"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-collection",
    libraryDependencies ++= Seq(
      guavaLib,
      scalacheckLib
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
      "com.twitter.common" % "objectsize" % "0.0.11" % "test",
      scalacheckLib,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"
    ),
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

  lazy val utilDoc = Project(
    id = "util-doc",
    base = file("doc"),
    settings = Defaults.coreDefaultSettings ++ site.settings ++ site.sphinxSupport() ++ sharedSettings ++ Seq(
      scalacOptions in doc <++= version.map(v => Seq("-doc-title", "Util", "-doc-version", v)),
      includeFilter in Sphinx := ("*.html" | "*.png" | "*.svg" | "*.js" | "*.css" | "*.gif" | "*.txt")
  ))

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
    libraryDependencies += scalacheckLib
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

  lazy val utilSecurity = Project(
    id = "util-security",
    base = file("util-security"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-security"
  ).dependsOn(utilCore, utilLogging)

  lazy val utilStats = Project(
    id = "util-stats",
    base = file("util-stats"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-stats",
    libraryDependencies += scalacheckLib
  ).dependsOn(utilCore, utilLint)

  lazy val utilTest = Project(
    id = "util-test",
    base = file("util-test"),
    settings = Defaults.coreDefaultSettings ++
      sharedSettings
  ).settings(
    name := "util-test",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "2.2.6",
      "org.mockito" % "mockito-all" % "1.10.19"
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
      "com.twitter"                % "libthrift"        % "0.5.0-7",
      "org.slf4j"                  % "slf4j-api"        % "1.7.7" % "provided",
      "com.fasterxml.jackson.core" % "jackson-core"     % "2.8.3",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.3"
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
      commonsZookeeper("client", zkClientVersion),
      commonsZookeeper("group", zkGroupVersion),
      commonsZookeeper("server-set", "1.0.111"),
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
      "com.twitter.common" % "io" % "0.0.68" % "test",
      zkDependency
    )
  )

}
