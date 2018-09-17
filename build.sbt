import scoverage.ScoverageKeys

// All Twitter library releases are date versioned as YY.MM.patch
val releaseVersion = "18.10.0-SNAPSHOT"

val zkVersion = "3.5.0-alpha"
val zkClientVersion = "0.0.81"
val zkGroupVersion = "0.0.92"
val zkDependency = "org.apache.zookeeper" % "zookeeper" % zkVersion excludeAll(
  ExclusionRule("com.sun.jdmk", "jmxtools"),
  ExclusionRule("com.sun.jmx", "jmxri"),
  ExclusionRule("javax.jms", "jms")
)
val slf4jVersion = "1.7.21"
val jacksonVersion = "2.9.6"

val guavaLib = "com.google.guava" % "guava" % "19.0"
val caffeineLib = "com.github.ben-manes.caffeine" % "caffeine" % "2.3.4"
val jsr305Lib = "com.google.code.findbugs" % "jsr305" % "2.0.1"
val scalacheckLib = "org.scalacheck" %% "scalacheck" % "1.13.4" % "test"
val slf4jApi = "org.slf4j" % "slf4j-api" % slf4jVersion
val slf4jSimple = "org.slf4j" % "slf4j-simple" % slf4jVersion

val defaultProjectSettings = Seq(
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq("2.11.11", "2.12.4")
)

val baseSettings = Seq(
  version := releaseVersion,
  organization := "com.twitter",
  // Workaround for a scaladoc bug which causes it to choke on empty classpaths.
  unmanagedClasspath in Compile += Attributed.blank(new java.io.File("doesnotexist")),
  libraryDependencies ++= Seq(
    // See http://www.scala-sbt.org/0.13/docs/Testing.html#JUnit
    "com.novocode" % "junit-interface" % "0.11" % "test",
    "org.mockito" % "mockito-all" % "1.10.19" % "test",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test"
  ),

  ScoverageKeys.coverageHighlighting := true,
  resolvers +=
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",

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

  // -a: print stack traces for failing asserts
  testOptions += Tests.Argument(TestFrameworks.JUnit, "-a"),

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

val sharedSettings = defaultProjectSettings ++ baseSettings

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  // sbt-pgp's publishSigned task needs this defined even though it is not publishing.
  publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo")))
)

lazy val util = Project(
  id = "util",
  base = file(".")
).enablePlugins(
  ScalaUnidocPlugin
).settings(
  sharedSettings ++
    noPublishSettings ++
    Seq(
      unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(utilBenchmark)
    )
).aggregate(
  utilApp,
  utilBenchmark,
  utilCache,
  utilCacheGuava,
  utilClassPreloader,
  utilCodec,
  utilCollection,
  utilCore,
  utilDoc,
  utilFunction,
  utilHashing,
  utilJvm,
  utilLint,
  utilLogging,
  utilReflect,
  utilRegistry,
  utilSecurity,
  utilSlf4jApi,
  utilSlf4jJulBridge,
  utilStats,
  utilTest,
  utilThrift,
  utilTunable,
  utilZk,
  utilZkTest
)

lazy val utilApp = Project(
  id = "util-app",
  base = file("util-app")
).settings(
  sharedSettings
).settings(
  name := "util-app"
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
).dependsOn(utilCore, utilHashing, utilJvm, utilStats)

lazy val utilCache = Project(
  id = "util-cache",
  base = file("util-cache")
).settings(
  sharedSettings
).settings(
  name := "util-cache",
  libraryDependencies ++= Seq(caffeineLib, jsr305Lib)
).dependsOn(utilCore)

lazy val utilCacheGuava = Project(
  id = "util-cache-guava",
  base = file("util-cache-guava")
).settings(
  sharedSettings
).settings(
  name := "util-cache-guava",
  libraryDependencies ++= Seq(guavaLib, jsr305Lib)
).dependsOn(utilCache % "compile->compile;test->test", utilCore)

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
    caffeineLib % "test",
    scalacheckLib,
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"
  ),
  resourceGenerators in Compile += Def.task {
    val projectName = name.value
    val file = resourceManaged.value / "com" / "twitter" / projectName / "build.properties"
    val buildRev = scala.sys.process.Process("git" :: "rev-parse" :: "HEAD" :: Nil).!!.trim
    val buildName = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date)
    val contents = s"name=$projectName\nversion=${version.value}\nbuild_revision=$buildRev\nbuild_name=$buildName"
    IO.write(file, contents)
    Seq(file)
  }.taskValue,
  sources in (Compile, doc) := {
    // Monitors.java causes "not found: type Monitor$" (CSL-5034)
    // so exclude it from the sources used for scaladoc
    val previous = (sources in (Compile, doc)).value
    previous.filterNot(file => file.getName() == "Monitors.java")
  }
).dependsOn(utilFunction)

lazy val utilDoc = Project(
  id = "util-doc",
  base = file("doc")
).enablePlugins(
  SphinxPlugin
).settings(
  sharedSettings ++ 
  noPublishSettings ++ Seq(
    scalacOptions in doc ++= Seq("-doc-title", "Util", "-doc-version", version.value),
    includeFilter in Sphinx := ("*.html" | "*.png" | "*.svg" | "*.js" | "*.css" | "*.gif" | "*.txt")
  ))

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

lazy val utilIntellij = Project(
  id = "util-intellij",
  base = file("util-intellij")
).settings(
  baseSettings
).settings(
  name := "util-intellij",
  scalaVersion := "2.11.11"
).dependsOn(utilCore % "test")

lazy val utilJvm = Project(
  id = "util-jvm",
  base = file("util-jvm")
).settings(
  sharedSettings
).settings(
  name := "util-jvm"
).dependsOn(utilApp, utilCore, utilStats, utilTest % "test")

lazy val utilLint = Project(
  id = "util-lint",
  base = file("util-lint")
).settings(
  sharedSettings
).settings(
  name := "util-lint"
).dependsOn(utilCore)

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
    slf4jApi,
    slf4jSimple 
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
    slf4jApi,
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
  libraryDependencies ++= Seq(caffeineLib, jsr305Lib, scalacheckLib)
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
    "org.apache.thrift"          % "libthrift"        % "0.10.0",
    slf4jApi % "provided",
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
