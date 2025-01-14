ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

val http4sVersion = "0.23.12"
val circeVersion = "0.14.10"


lazy val root = (project in file("."))
  .settings(
    name := "http4s-simple-server",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl"          % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "org.http4s" %% "http4s-circe"        % http4sVersion,
      "io.circe" %% "circe-generic" % circeVersion,
    )
  )
