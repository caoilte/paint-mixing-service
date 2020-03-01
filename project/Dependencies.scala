import sbt.Keys._
import sbt.{Def, _}

object Dependencies extends AutoPlugin {

  private val dependencies = Seq(
    "io.circe" %% "circe-core" % "0.12.2",
    "io.circe" %% "circe-generic" % "0.12.2",
    "io.circe" %% "circe-refined" % "0.12.2",
    "io.circe" %% "circe-parser" % "0.12.2" % Test,
    "io.circe" %% "circe-literal" % "0.12.2" % Test,
    "org.http4s" %% "http4s-circe" % "0.21.0-M5",
    "org.http4s" %% "http4s-client" % "0.21.0-M5",
    "org.http4s" %% "http4s-core" % "0.21.0-M5",
    "org.http4s" %% "http4s-dsl" % "0.21.0-M5",
    "org.http4s" %% "http4s-async-http-client" % "0.21.0-M5",
    "org.asynchttpclient" % "async-http-client" % "2.10.4",
    "io.netty" % "netty-codec-http" % "4.1.43.Final",
    "org.typelevel" %% "cats-core" % "2.0.0",
    "org.typelevel" %% "cats-effect" % "2.0.0",
    "org.typelevel" %% "cats-free" % "2.0.0",
    "org.typelevel" %% "cats-mtl-core" % "0.7.0",
    "org.typelevel" %% "cats-tagless-core" % "0.9",
    "org.typelevel" %% "cats-tagless-macros" % "0.9",
    "com.whisk" %% "docker-testkit-core" % "0.9.9" % Test,
    "com.whisk" %% "docker-testkit-impl-spotify" % "0.9.9" % Test,
    "com.whisk" %% "docker-testkit-scalatest" % "0.9.9" % Test,
    "org.scalatest" %% "scalatest" % "3.1.1" % Test,
  )

  private val server = Seq(
    "io.chrisdavenport" %% "log4cats-core" % "1.0.0",
    "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.0",
    "io.chrisdavenport" %% "log4cats-mtl" % "0.1.0",
    "org.http4s" %% "http4s-blaze-server" % "0.21.0-M5",
    "org.http4s" %% "http4s-server" % "0.21.0-M5",
    "org.slf4j" % "slf4j-api" % "1.7.27",
  )

  private val enumeratum = Seq(
    "com.beachape" %% "enumeratum" % "1.5.13",
    "com.beachape" %% "enumeratum-doobie" % "1.5.15",
    "com.beachape" %% "enumeratum-circe" % "1.5.22"
  )

  private val defaultDependencies: Def.Setting[Seq[ModuleID]] =
    libraryDependencies ++= dependencies ++ server ++ enumeratum

  object autoImport {

    implicit final class DependenciesProject(val project: Project) extends AnyVal {

      def withDependencies: Project =
        project
          .settings(defaultDependencies)
          .settings(addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.10"))
          .settings(addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"))
    }
  }

}
