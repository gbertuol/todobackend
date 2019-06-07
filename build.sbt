
lazy val root = project
  .in(file("."))
  .settings(
      name := "todobackend",
      organization := "bertuol",
      scalaVersion := "2.12.8"
  )

val Http4sVersion = "0.20.0"

libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest"           % "3.0.5" % Test,
    "org.typelevel" %% "cats-effect"         % "1.3.0",
    "org.http4s"    %% "http4s-blaze-server" % Http4sVersion,
    "org.http4s"    %% "http4s-blaze-client" % Http4sVersion,
    "org.http4s"    %% "http4s-circe"        % Http4sVersion,
    "org.http4s"    %% "http4s-dsl"          % Http4sVersion,
    "io.circe"      %% "circe-generic"       % "0.11.1"
)

scalacOptions in Compile in console := Seq(
    "-Ypartial-unification",
    "-language:higherKinds",
    "-language:existentials",
    "-Yno-adapted-args",
    "-Xsource:2.13",
    "-Yrepl-class-based",
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-explaintypes",
    "-Yrangepos",
    "-feature",
    "-Xfuture",
    "-unchecked",
    "-Xlint:_,-type-parameter-shadow",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-opt-warnings",
    "-Ywarn-extra-implicit",
    "-Ywarn-unused:_,imports",
    "-Ywarn-unused:imports",
    "-opt:l:inline",
    "-opt-inline-from:<source>",
    "-Ypartial-unification",
    "-Yno-adapted-args",
    "-Ywarn-inaccessible",
    "-Ywarn-infer-any",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit"
)