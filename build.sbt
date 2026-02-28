ThisBuild / tlBaseVersion := "1.0"

ThisBuild / organization := "dev.holt"
ThisBuild / organizationName := "PlanetHolt"
ThisBuild / homepage := Option(url("https://github.com/bpholt/fake-ec2-metadata-service"))
ThisBuild / startYear := Some(2015)
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  tlGitHubDev("bpholt", "Brian Holt")
)

ThisBuild / scalaVersion := "2.13.18"
ThisBuild / githubWorkflowScalaVersions := Seq("2.13")
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowTargetTags := Seq("v*")
ThisBuild / githubWorkflowBuild += WorkflowStep.Sbt(List("Docker/stage"))

ThisBuild / githubWorkflowPublishPreamble := Seq(
  WorkflowStep.Use(name = Option("Set up QEMU"), ref = UseRef.Public("docker", "setup-qemu-action", "v2")),
  WorkflowStep.Use(name = Option("Set up Docker Buildx"), ref = UseRef.Public("docker", "setup-buildx-action", "v2")),
  WorkflowStep.Use(name = Option("DockerHub Login"), ref = UseRef.Public("docker", "login-action", "v2"), params = Map(
    "username" -> "${{ secrets.DOCKERHUB_USERNAME }}",
    "password" -> "${{ secrets.DOCKERHUB_TOKEN }}",
  )),
)
ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Use(id = Option("meta"), name = Option("Docker meta"), ref = UseRef.Public("docker", "metadata-action", "v4"), params = Map(
    "images" ->
      (`fake-ec2-metadata-service` / dockerUsername)
        .value
        .foldRight((`fake-ec2-metadata-service` / Docker / name).value) {
          List(_, _).mkString("/")
        },
    "tags" -> "type=semver,pattern={{raw}}"
  )),
  WorkflowStep.Use(name = Option("Build and push"), ref = UseRef.Public("docker", "build-push-action", "v4"), params = Map(
    // https://github.com/sbt/sbt-native-packager/issues/1699
    "context" -> (`fake-ec2-metadata-service` / Docker / UniversalPlugin.autoImport.stagingDirectory).value.relativeTo((root / baseDirectory).value).get.getPath,
    "platforms" -> "linux/amd64,linux/arm64",
    "push" -> "${{ github.event_name != 'pull_request' && (startsWith(github.ref, 'refs/tags/v')) }}",
    "tags" -> "${{ steps.meta.outputs.tags }}",
  )),
)
ThisBuild / githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v")))
ThisBuild / tlCiMimaBinaryIssueCheck := false
ThisBuild / mergifyStewardConfig ~= { _.map {
  _.withMergeMinors(true)
}}

lazy val root = project.in(file(".")).enablePlugins(NoPublishPlugin).aggregate(
  `fake-ec2-metadata-service`
)

lazy val `fake-ec2-metadata-service` = project
  .in(file("core"))
  .settings(
    name := "fake-ec2-metadata-service",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % "0.23.33",
      "org.http4s" %% "http4s-dsl" % "0.23.33",
      "org.http4s" %% "http4s-circe" % "0.23.33",
      "com.comcast" %% "ip4s-core" % "3.7.0",
      "io.circe" %% "circe-core" % "0.14.15",
      "io.circe" %% "circe-generic" % "0.14.15",
      "io.circe" %% "circe-literal" % "0.14.15",
      "dev.holt" %% "java-time-literals" % "1.1.1",
      "software.amazon.awssdk" % "profiles" % "2.42.4",
      "io.monix" %% "newtypes-core" % "0.3.0",
      "org.typelevel" %% "mouse" % "1.4.0",
      "ch.qos.logback" % "logback-classic" % "1.5.32" % Runtime,
      "org.typelevel" %% "munit-cats-effect" % "2.1.0" % Test,
      "org.typelevel" %% "scalacheck-effect-munit" % "2.0.0-M2" % Test,
      "org.http4s" %% "http4s-client" % "0.23.33" % Test,
      "com.comcast" %% "ip4s-test-kit" % "3.7.0" % Test,
      "eu.timepit" %% "refined-scalacheck" % "0.11.3" % Test,
      "org.typelevel" %% "cats-testkit" % "2.13.0" % Test,
      "org.typelevel" %% "cats-laws" % "2.13.0" % Test,
      "org.typelevel" %% "discipline-munit" % "2.0.0" % Test,
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
