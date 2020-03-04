import com.typesafe.sbt.SbtNativePackager.autoImport.{maintainer, packageDescription, packageName, packageSummary}
import com.typesafe.sbt.packager.Keys.{dockerAlias, dockerBuildOptions, dockerCommands, dockerUpdateLatest}
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.archetypes.scripts.AshScriptPlugin
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker => TypesafeDocker, _}
import com.typesafe.sbt.packager.docker.{Cmd, DockerAlias, DockerPlugin}
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.Universal
import sbt.Keys._
import sbt.io.FileFilter._
import sbt.io.PathFinder
import sbt.{AutoPlugin, Compile, Project}

object Docker extends AutoPlugin {

  private val default =
    Seq(
      aggregate in TypesafeDocker := false,
      mainClass in Compile := Some("org.caoilte.paint.Main"),
      dockerExposedPorts ++= Seq(9090)
    )

  object autoImport {

    implicit final class DockerSettings(val project: Project) extends AnyVal {

      def withDocker: Project =
        project
          .enablePlugins(JavaAppPackaging, DockerPlugin)
          .settings(default)
    }

  }

}
