
scalaVersion := "2.13.5"

val circeVersion = "0.12.3"
val AkkaVersion = "2.6.8"
val AkkaHttpVersion = "10.2.4"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,

  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion
)

libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.30"

// Guardrail on compile
guardrailTasks in Compile := List(
  ScalaClient(file("neocities.yaml"), pkg="generated.clients.Neocities", framework="akka-http")
)

assemblyJarName in assembly := "cli.jar"