import Dependencies._

scalaVersion := Version.scala
scalaVersion in ThisBuild := Version.scala

lazy val commonSettings = Seq(
  scalaVersion := Version.scala,
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),
  homepage := Some(url(Info.url)),
  scmInfo := Some(ScmInfo(
    url("https://github.com/geotrellis/geotrellis-contrib"), "scm:git:git@github.com:geotrellis/geotrellis-contrib.git"
  )),
  scalacOptions ++= Seq(
    "-deprecation", "-unchecked", "-feature",
    "-language:implicitConversions",
    "-language:reflectiveCalls",
    "-language:higherKinds",
    "-language:postfixOps",
    "-language:existentials",
    "-language:experimental.macros",
    "-Ypartial-unification" // Required by Cats
  ),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.4" cross CrossVersion.binary),
  addCompilerPlugin("org.scalamacros" %% "paradise" % "2.1.1" cross CrossVersion.full),
  dependencyUpdatesFilter := moduleFilter(organization = "org.scala-lang"),
  resolvers ++= Seq(
    "geosolutions" at "http://maven.geo-solutions.it/",
    "locationtech-releases" at "https://repo.locationtech.org/content/groups/releases",
    "locationtech-snapshots" at "https://repo.locationtech.org/content/groups/snapshots",
    "osgeo" at "http://download.osgeo.org/webdav/geotools/"
  ),
  headerLicense := Some(HeaderLicense.ALv2("2018", "Azavea")),
  bintrayOrganization := Some("azavea"),
  bintrayRepository := "geotrellis",
  bintrayPackageLabels := Seq("gis", "raster", "vector"),
  updateOptions := updateOptions.value.withLatestSnapshots(false)
)

lazy val root = Project("geotrellis-contrib", file(".")).
  aggregate(
    vlm
  ).
  settings(commonSettings: _*).
  settings(publish / skip := true).
  disablePlugins(LighterPlugin).
  settings(
    initialCommands in console :=
      """
      """
  )

lazy val vlm = project
  .settings(commonSettings)
  .disablePlugins(LighterPlugin)
  .settings(
    organization := "com.azavea.geotrellis",
    name := "geotrellis-contrib-vlm",
    libraryDependencies ++= Seq(
      geotrellisSpark,
      geotrellisS3,
      geotrellisUtil,
      gdal,
      scalactic,
      squants,
      sparkCore % Provided,
      sparkSQL % Test,
      geotrellisSparkTestKit % Test,
      scalatest % Test
    ),
    Test / fork := true,
    Test / parallelExecution := false,
    Test / testOptions += Tests.Argument("-oD"),
    javaOptions ++= Seq("-Xms1024m", "-Xmx6144m", "-Djava.library.path=/usr/local/lib")
  )

import sbtlighter._

lazy val wse = project
  .settings(commonSettings)
  .dependsOn(vlm)
  .settings(
    name := "wse-demo",
    libraryDependencies ++= Seq(
      geotrellisSpark,
      geotrellisS3,
      geotrellisShapefile,
      "org.locationtech.geotrellis" %% "geotrellis-geotools" % Version.geotrellis,
      "org.geotools" % "gt-ogr-bridj" % Version.geotools
        exclude("com.nativelibs4java", "bridj"),
      "org.geotools" % "gt-cql" % Version.geotools,
      "com.nativelibs4java" % "bridj" % "0.6.1",
      sparkCore % Provided,
      squants
    ),
    Test / fork := true,
    Test / parallelExecution := false,
    Test / testOptions += Tests.Argument("-oD"),
    javaOptions ++= Seq("-Xms1024m", "-Xmx6144m", "-Djava.library.path=/usr/local/lib"),
    sparkAwsRegion              := "us-east-1",
    sparkS3JarFolder            := "s3://geotrellis-test/eac/jars/",
    sparkInstanceCount          := 0,
    sparkMasterType             := "m4.xlarge",
    sparkCoreType               := "m4.xlarge",
    sparkMasterPrice            := Some(0.5),
    sparkCorePrice              := Some(0.5),
    sparkEmrRelease             := "emr-5.17.0",
    sparkAwsRegion              := "us-east-1",
    sparkSubnetId               := Some("subnet-4f553375"),
    sparkClusterName            := s"wce-demo",
    sparkEmrServiceRole         := "EMR_DefaultRole",
    sparkInstanceRole           := "EMR_EC2_DefaultRole",
    sparkJobFlowInstancesConfig := sparkJobFlowInstancesConfig.value.withEc2KeyName("geotrellis-emr"),
    sparkEmrBootstrap           := Seq(
      BootstrapAction("first", "s3://geotrellis-test/bootstrap.sh"),
      BootstrapAction("second", "s3://geotrellis-test/bootstrap2.sh", "--full")
    ),
    sparkEmrConfigs             := List(
      EmrConfig("spark").withProperties(
        "maximizeResourceAllocation" -> "true"
      ),
      EmrConfig("spark-defaults").withProperties(
        "spark.driver.maxResultSize" -> "3G",
        "spark.dynamicAllocation.enabled" -> "true",
        "spark.shuffle.service.enabled" -> "true",
        "spark.shuffle.compress" -> "true",
        "spark.shuffle.spill.compress" -> "true",
        "spark.rdd.compress" -> "true",
        "spark.yarn.executor.memoryOverhead" -> "1G",
        "spark.yarn.driver.memoryOverhead" -> "1G",
        "spark.driver.maxResultSize" -> "3G",
        "spark.executor.extraJavaOptions" -> "-XX:+UseParallelGC -Dgeotrellis.s3.threads.rdd.write=64"

      ),
      EmrConfig("yarn-site").withProperties(
        "yarn.resourcemanager.am.max-attempts" -> "1",
        "yarn.nodemanager.vmem-check-enabled" -> "false",
        "yarn.nodemanager.pmem-check-enabled" -> "false"
      )
    )
  )

lazy val benchmark = (project in file("benchmark"))
  .settings(commonSettings: _*)
  .settings( publish / skip := true)
  .disablePlugins(LighterPlugin)