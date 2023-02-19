ThisBuild / tlBaseVersion := "1.0"

ThisBuild / organization := "dev.holt"
ThisBuild / organizationName := "PlanetHolt"
ThisBuild / startYear := Some(2015)
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  tlGitHubDev("bpholt", "Brian Holt")
)

ThisBuild / scalaVersion := "2.13.10"
ThisBuild / githubWorkflowScalaVersions := Seq("2.13")
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowPublishPreamble := Seq(
  WorkflowStep.Use(name = Option("DockerHub Login"), ref = UseRef.Public("docker", "login-action", "v2"), params = Map(
    "username" -> "${{ secrets.DOCKERHUB_USERNAME }}",
    "password" -> "${{ secrets.DOCKERHUB_TOKEN }}",
  ))
)
ThisBuild / githubWorkflowPublish := Seq(WorkflowStep.Sbt(List("Docker/publish")))
ThisBuild / githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v")))
ThisBuild / tlCiMimaBinaryIssueCheck := false

lazy val root = project.in(file(".")).enablePlugins(NoPublishPlugin).aggregate(
  `fake-ec2-metadata-service`
)

lazy val `fake-ec2-metadata-service` = project
  .in(file("core"))
  .settings(
    name := "fake-ec2-metadata-service",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % "0.23.18",
      "org.http4s" %% "http4s-dsl" % "0.23.18",
      "org.http4s" %% "http4s-circe" % "0.23.18",
      "io.circe" %% "circe-core" % "0.14.4",
      "io.circe" %% "circe-generic" % "0.14.4",
      "dev.holt" %% "java-time-literals" % "1.1.0",
      "software.amazon.awssdk" % "profiles" % "2.20.7",
      "ch.qos.logback" % "logback-classic" % "1.4.5" % Runtime,
      "org.typelevel" %% "munit-cats-effect" % "2.0.0-M3" % Test,
      "org.typelevel" %% "scalacheck-effect-munit" % "2.0.0-M2" % Test,
      "org.http4s" %% "http4s-client" % "0.23.18" % Test,
      "com.comcast" %% "ip4s-test-kit" % "3.2.0" % Test,
      "eu.timepit" %% "refined-scalacheck" % "0.10.1" % Test,
      "org.typelevel" %% "cats-testkit" % "2.9.0" % Test,
      "io.circe" %% "circe-literal" % "0.14.4" % Test,
    ),
    dockerUsername := Option("bpholt"),
    dockerBaseImage := "eclipse-temurin:17",
    dockerExposedPorts += 8169,
    dockerUpdateLatest := true,
  )
  .enablePlugins(JavaServerAppPackaging, DockerPlugin)
