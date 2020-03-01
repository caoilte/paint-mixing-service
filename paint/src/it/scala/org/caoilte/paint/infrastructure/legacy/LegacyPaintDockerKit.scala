package org.caoilte.paint.infrastructure.legacy

import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.scalatest.DockerTestKit
import com.whisk.docker.{DockerContainer, DockerReadyChecker}
import org.scalatest.Suite

import scala.concurrent.duration._

trait LegacyPaintDockerKit extends DockerKitSpotify with DockerTestKit {
  this: Suite =>

  type PortMapping = (Int, Option[Int])

  final val paintContainerPort: Int = 8080
  final val paintAdvertisedPort: Int = 8081

  def paintPortMappings: Seq[PortMapping] =
    Seq(paintContainerPort -> Some(paintAdvertisedPort))

  val legacyPaintContainer: DockerContainer =
    DockerContainer(s"legacy-paint", name = Some("legacy-paint-running"))
      .withPorts(paintPortMappings: _*)
      .withReadyChecker(DockerReadyChecker.HttpResponseCode(
        paintContainerPort,
        "/v1/?input={%22colors%22:5,%22customers%22:3,%22demands%22:[[1,1,1],[2,1,0,2,0],[1,5,0]]}",
        Some("localhost")).looped(5, delay = 8 seconds))

  override def dockerContainers: List[DockerContainer] =
    legacyPaintContainer :: super.dockerContainers
}
