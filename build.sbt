import com.typesafe.sbt.SbtSite.site
import com.typesafe.sbt.site.SphinxSupport.Sphinx
import sbtunidoc.Plugin.UnidocKeys._
import sbtunidoc.Plugin.{ScalaUnidoc, unidocSettings}
import scoverage.ScoverageKeys

val branch = Process("git" :: "rev-parse" :: "--abbrev-ref" :: "HEAD" :: Nil).!!.trim
val suffix = if (branch == "master") "" else "-SNAPSHOT"

val libVersion = "6.45.0" + suffix
val zkVersion = "3.5.0-alpha"
val zkClientVersion = "0.0.81"
val zkGroupVersion = "0.0.92"
val zkDependency = "org.apache.zookeeper" % "zookeeper" % zkVersion excludeAll(
  ExclusionRule("com.sun.jdmk", "jmxtools"),
  ExclusionRule("com.sun.jmx", "jmxri"),
  ExclusionRule("javax.jms", "jms")
)
val slf4jVersion = "1.7.21"
val jacksonVersion = "2.8.4"

val guavaLib = "com.google.guava" % "guava" % "19.0"
val caffeineLib = "com.github.ben-manes.caffeine" % "caffeine" % "2.3.4"
val jsr305Lib = "com.google.code.findbugs" % "jsr305" % "2.0.1"
val scalacheckLib = "org.scalacheck" %% "scalacheck" % "1.13.4" % "test"
val slf4jLib = "org.slf4j" % "slf4j-api" % slf4jVersion

val sharedSettings = Seq(
  version := libVersion,
  organization := "com.twitter",
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.11.11", "2.12.1"),
  // Workaround for a scaladoc bug which causes it to choke on empty classpaths.
  unmanagedClasspath in Compile += Attributed.blank(new java.io.File("doesnotexist")),
  libraryDependencies ++= Seq(
    "junit" % "junit" % "4.8.1" % "test",
    "org.mockito" % "mockito-all" % "1.10.19" % "test",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test"
  ),

  ScoverageKeys.coverageHighlighting := true,

  scalacOptions := Seq(
    // Note: Add -deprecation when deprecated methods are removed
    "-target:jvm-1.8",
    "-unchecked",
    "-feature",
    "-encoding", "utf8",
    // Needs -missing-interpolator due to https://issues.scala-lang.org/browse/SI-8761
    "-Xlint:-missing-interpolator"
  ),

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
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  }
)

lazy val util = Project(
  id = "util",
  base = file(".")
).settings(
  sharedSettings ++
    unidocSettings ++
    Seq(
      unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(utilBenchmark)
    )
).aggregate(
  utilFunction, utilRegistry, utilCore, utilCodec, utilCollection, utilCache, utilDoc, utilReflect,
  utilLint, utilLogging, utilSlf4jApi, utilSlf4jJulBridge, utilTest, utilThrift, utilHashing, utilJvm, utilZk,
  utilZkTest, utilClassPreloader, utilBenchmark, utilApp, utilEvents, utilSecurity, utilStats, utilTunable
)

lazy val utilApp = Project(
  id = "util-app",
  base = file("util-app")
).settings(
  sharedSettings
).settings(
  name := "util-app",
  libraryDependencies ++= Seq(
    guavaLib
  )
).dependsOn(utilCore, utilRegistry)

lazy val utilBenchmark = Project(
  id = "util-benchmark",
  base = file("util-benchmark")
).settings(
  sharedSettings
).enablePlugins(
  JmhPlugin
).settings(
  name := "util-benchmark"
).dependsOn(utilCore, utilEvents, utilHashing, utilJvm, utilStats)

lazy val utilCache = Project(
  id = "util-cache",
  base = file("util-cache")
).settings(
  sharedSettings
).settings(
  name := "util-cache",
  libraryDependencies ++= Seq(guavaLib, caffeineLib, jsr305Lib)
).dependsOn(utilCore)

lazy val utilClassPreloader = Project(
  id = "util-class-preloader",
  base = file("util-class-preloader")
).settings(
  sharedSettings
).settings(
  name := "util-class-preloader"
).dependsOn(utilCore)

lazy val utilCodec = Project(
  id = "util-codec",
  base = file("util-codec")
).settings(
  sharedSettings
).settings(
  name := "util-codec"
).dependsOn(utilCore)

lazy val utilCollection = Project(
  id = "util-collection",
  base = file("util-collection")
).settings(
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
  base = file("util-core")
).settings(
  sharedSettings
).settings(
  name := "util-core",
  libraryDependencies ++= Seq(
    guavaLib % "test",
    scalacheckLib,
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"
  ),
  resourceGenerators in Compile += Def.task {
    val projectName = name.value
    val file = resourceManaged.value / "com" / "twitter" / projectName / "build.properties"
    val buildRev = Process("git" :: "rev-parse" :: "HEAD" :: Nil).!!.trim
    val buildName = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date)
    val contents = s"name=$projectName\nversion=${version.value}\nbuild_revision=$buildRev\nbuild_name=$buildName"
    IO.write(file, contents)
    Seq(file)
  }.taskValue
).dependsOn(utilFunction)

lazy val utilDoc = Project(
  id = "util-doc",
  base = file("doc")
).settings(
  site.settings ++ site.sphinxSupport() ++ sharedSettings ++ Seq(
    scalacOptions in doc ++= Seq("-doc-title", "Util", "-doc-version", version.value),
    includeFilter in Sphinx := ("*.html" | "*.png" | "*.svg" | "*.js" | "*.css" | "*.gif" | "*.txt")
  ))

lazy val utilEvents = Project(
  id = "util-events",
  base = file("util-events")
).settings(
  sharedSettings
).settings(
  name := "util-events"
).dependsOn(utilApp)

lazy val utilFunction = Project(
  id = "util-function",
  base = file("util-function")
).settings(
  sharedSettings
).settings(
  name := "util-function"
)

lazy val utilReflect = Project(
  id = "util-reflect",
  base = file("util-reflect")
).settings(
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
  base = file("util-hashing")
).settings(
  sharedSettings
).settings(
  name := "util-hashing",
  libraryDependencies += scalacheckLib
).dependsOn(utilCore % "test")

lazy val utilJvm = Project(
  id = "util-jvm",
  base = file("util-jvm")
).settings(
  sharedSettings
).settings(
  name := "util-jvm"
).dependsOn(utilApp, utilCore, utilTest % "test")

lazy val utilLint = Project(
  id = "util-lint",
  base = file("util-lint")
).settings(
  sharedSettings
).settings(
  name := "util-lint"
)

lazy val utilLogging = Project(
  id = "util-logging",
  base = file("util-logging")
).settings(
  sharedSettings
).settings(
  name := "util-logging"
).dependsOn(utilCore, utilApp, utilStats)

lazy val utilSlf4jApi = Project(
  id = "util-slf4j-api",
  base = file("util-slf4j-api")
).settings(
  sharedSettings
).settings(
  name := "util-slf4j-api",
  libraryDependencies ++= Seq(
    slf4jLib
  )
).dependsOn(utilCore % "test")

lazy val utilSlf4jJulBridge = Project(
  id = "util-slf4j-jul-bridge",
  base = file("util-slf4j-jul-bridge")
).settings(
  sharedSettings
).settings(
  name := "util-slf4j-jul-bridge",
  libraryDependencies ++= Seq(
    slf4jLib,
    "org.slf4j" % "jul-to-slf4j" % slf4jVersion)
).dependsOn(utilCore, utilSlf4jApi)

lazy val utilRegistry = Project(
  id = "util-registry",
  base = file("util-registry")
).settings(
  sharedSettings
).settings(
  name := "util-registry"
).dependsOn(utilCore)

lazy val utilSecurity = Project(
  id = "util-security",
  base = file("util-security")
).settings(
  sharedSettings
).settings(
  name := "util-security"
).dependsOn(utilCore, utilLogging)

lazy val utilStats = Project(
  id = "util-stats",
  base = file("util-stats")
).settings(
  sharedSettings
).settings(
  name := "util-stats",
  libraryDependencies ++= Seq(caffeineLib, jsr305Lib, scalacheckLib, guavaLib % "test")
).dependsOn(utilCore, utilLint)

lazy val utilTest = Project(
  id = "util-test",
  base = file("util-test")
).settings(
  sharedSettings
).settings(
  name := "util-test",
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.0",
    "org.mockito" % "mockito-all" % "1.10.19"
  )
).dependsOn(utilCore, utilLogging)

lazy val utilThrift = Project(
  id = "util-thrift",
  base = file("util-thrift")
).settings(
  sharedSettings
).settings(
  name := "util-thrift",
  libraryDependencies ++= Seq(
    "com.twitter"                % "libthrift"        % "0.5.0-7",
    slf4jLib % "provided",
    "com.fasterxml.jackson.core" % "jackson-core"     % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion
  )
).dependsOn(utilCodec)

lazy val utilTunable = Project(
  id = "util-tunable",
  base = file("util-tunable")
).settings(
  sharedSettings
).settings(
  name := "util-tunable",
  libraryDependencies ++= Seq(
    "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion exclude("com.google.guava", "guava")
  )
).dependsOn(utilApp, utilCore)

lazy val utilZk = Project(
  id = "util-zk",
  base = file("util-zk")
).settings(
  sharedSettings
).settings(
  name := "util-zk",
  libraryDependencies += zkDependency
).dependsOn(utilCore, utilCollection, utilLogging)

lazy val utilZkTest = Project(
  id = "util-zk-test",
  base = file("util-zk-test")
).settings(
  sharedSettings
).settings(
  name := "util-zk-test",
  libraryDependencies += zkDependency
).dependsOn(utilCore % "test")
