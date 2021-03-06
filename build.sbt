
lazy val root = project
  .in(file("."))
  .settings(
      name := "todobackend",
      organization := "bertuol",
      scalaVersion := "2.13.1"
  )

Test / fork := true
Test / javaOptions += "-Xmx2G"

val Http4sVersion = "0.21.0-M6"
val CirceVersion = "0.12.3"

libraryDependencies ++= Seq(
    "org.scalatest"     %% "scalatest"           % "3.1.0" % Test,
    "org.typelevel"     %% "cats-effect"         % "2.0.0",
    "org.http4s"        %% "http4s-blaze-server" % Http4sVersion,
    "org.http4s"        %% "http4s-blaze-client" % Http4sVersion,
    "org.http4s"        %% "http4s-circe"        % Http4sVersion,
    "org.http4s"        %% "http4s-dsl"          % Http4sVersion,
    "io.circe"          %% "circe-generic"       % CirceVersion,
    "io.circe"          %% "circe-literal"       % CirceVersion,
    "io.chrisdavenport" %% "log4cats-slf4j"      % "1.0.1",
    "com.monovore"      %% "decline-effect"      % "1.0.0",
    "org.apache.logging.log4j" % "log4j-api"        % "2.12.0",
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.12.0",
    "software.amazon.awssdk"   % "dynamodb"         % "2.5.64"
)
