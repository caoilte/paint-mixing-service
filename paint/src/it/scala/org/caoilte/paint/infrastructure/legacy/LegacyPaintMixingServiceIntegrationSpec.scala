package org.caoilte.paint.infrastructure.legacy

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO, Resource}
import cats.implicits._
import com.whisk.docker.scalatest.DockerTestKit
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.caoilte.paint.domain._
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

class LegacyPaintMixingServiceIntegrationSpec extends AnyFlatSpec with Matchers with DockerTestKit with LegacyPaintDockerKit {

  // make trait
  implicit lazy val ec: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  implicit protected def contextShift: ContextShift[IO] = IO.contextShift(ec)
  // make trait

  "Paint Mixing Service" should "work for happy and unhappy path" in new Scope {
    withService(service => {
      val result = service.preparationFor(
        PaintMixRequest(
          colors = 5,
          List(
            CustomerDemands(
              customerNumber = CustomerNumber(1),
              matteDemands = Option(Colour(1)),
              glossyDemands = Nil,
            ),
            CustomerDemands(
              customerNumber = CustomerNumber(2),
              matteDemands = None,
              glossyDemands = List(Colour(1), Colour(2)),
            ),
            CustomerDemands(
              customerNumber = CustomerNumber(3),
              matteDemands = None,
              glossyDemands = List(Colour(5)),
            )
          )
        )
      ).unsafeRunSync()
      result.get.paintMixes.sortBy(_.colour.value) should equal(List(
          PaintMix(Colour(1), PaintMixType.Matte),
          PaintMix(Colour(2), PaintMixType.Glossy),
          PaintMix(Colour(3), PaintMixType.Glossy),
          PaintMix(Colour(4), PaintMixType.Glossy),
          PaintMix(Colour(5), PaintMixType.Glossy),
      ))

      val result2 = service.preparationFor(
        PaintMixRequest(
          colors = 1,
          List(
            CustomerDemands(
              customerNumber = CustomerNumber(1),
              matteDemands = Option(Colour(1)),
              glossyDemands = Nil,
            ),
            CustomerDemands(
              customerNumber = CustomerNumber(2),
              matteDemands = None,
              glossyDemands = List(Colour(1)),
            ),
          )
        )
      ).unsafeRunSync()
      result2 should equal(None)


      ().pure[F]
    }).unsafeRunSync()
  }

  trait Scope {
    type F[A] = IO[A]

    val clientConfig = new DefaultAsyncHttpClientConfig.Builder().build()

    val clientR: Resource[F, Client[F]] = AsyncHttpClient.resource[F](clientConfig)

    def withService(testFunc: PaintMixingService[F] => F[Unit]): F[Unit] =
      clientR.use(client => {
        testFunc(new LegacyPaintMixingService[F](client, Uri.unsafeFromString("http://localhost:8081")))
      })

  }
}
