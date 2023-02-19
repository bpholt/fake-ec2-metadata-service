ThisBuild / tlBaseVersion := "1.0"

ThisBuild / organization := "dev.holt"
ThisBuild / organizationName := "PlanetHolt"
ThisBuild / homepage := Option(url("https://github.com/bpholt/fake-ec2-metadata-service"))
ThisBuild / startYear := Some(2015)
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  tlGitHubDev("bpholt", "Brian Holt")
)

ThisBuild / scalaVersion := "2.13.10"
ThisBuild / githubWorkflowScalaVersions := Seq("2.13")
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowTargetTags := Seq("v*")
ThisBuild / githubWorkflowBuild += WorkflowStep.Sbt(List("Docker/stage"))

ThisBuild / githubWorkflowPublishPreamble := Seq(
  WorkflowStep.Use(name = Option("Set up QEMU"), ref = UseRef.Public("docker", "setup-qemu-action", "v1")),
  WorkflowStep.Use(name = Option("Set up Docker Buildx"), ref = UseRef.Public("docker", "setup-buildx-action", "v1")),
  WorkflowStep.Use(name = Option("DockerHub Login"), ref = UseRef.Public("docker", "login-action", "v2"), params = Map(
    "username" -> "${{ secrets.DOCKERHUB_USERNAME }}",
    "password" -> "${{ secrets.DOCKERHUB_TOKEN }}",
  )),
)
ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Use(name = Option("Docker meta"), ref = UseRef.Public("docker", "metadata-action", "v3"), params = Map(
    "images" -> (`fake-ec2-metadata-service` / dockerUsername).value.foldLeft((`fake-ec2-metadata-service` / Docker / name).value) { (image, username) =>
      List(username, image).mkString("/")
    },
    "tags" -> "type=semver,pattern={{raw}}"
  )),
  WorkflowStep.Use(name = Option("Build and push"), ref = UseRef.Public("docker", "build-push-action", "v2"), params = Map(
    "context" -> (`fake-ec2-metadata-service` / Docker / stagingDirectory).value.relativeTo((root / baseDirectory).value).get.getPath,
    "platforms" -> "linux/amd64,linux/arm64",
    "push" -> "${{ github.event_name != 'pull_request' && (startsWith(github.ref, 'refs/tags/v')) }}",
    "tags" -> "${{ steps.meta.outputs.tags }}",
    "build-args" -> "${{ inputs.TAG_NAME }}=${{ inputs.BASE_TAG }}",
  )),
)
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
    dockerLabels :=
      Map(
        "org.opencontainers.image.version" -> version.value,
        "org.opencontainers.image.authors" -> developers.value.map(x => s"${x.name} <${x.url}>").mkString(", "),
      ) ++
        scmInfo.value.map(_.browseUrl.toString).map("org.opencontainers.image.source" -> _) ++
        git.gitHeadCommit.value.map("org.opencontainers.image.revision" -> _)
  )
  .enablePlugins(JavaServerAppPackaging, DockerPlugin)