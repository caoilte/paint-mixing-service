package org.caoilte.paint

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO, Resource}
import com.whisk.docker.scalatest.DockerTestKit
import io.circe.Json
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.caoilte.paint.domain.PaintMixingService
import org.caoilte.paint.infrastructure.api.{PaintApi, PaintRestService}
import org.caoilte.paint.infrastructure.legacy.{LegacyPaintDockerKit, LegacyPaintMixingService}
import org.http4s.client.Client
import org.http4s.{HttpRoutes, Uri}
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.http4s.implicits._
import io.circe.syntax._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

class PaintMixingEndToEndSpec extends AnyFlatSpec with Matchers with DockerTestKit with LegacyPaintDockerKit {

  // make trait
  implicit lazy val ec: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  implicit protected def contextShift: ContextShift[IO] = IO.contextShift(ec)
  // make trait

  behavior of "Paint Mixing Service"

  it should "execute the sad path" in new Scope {
    withRoutes(
      routes => {
        val api = routes.orNotFound

        val requestEntity = Json.obj(
          "colours" -> 1.asJson,
          "customers" -> 2.asJson,
          "customerDemands" -> Json.arr(
            Json.obj(
              "customerNumber" -> 1.asJson,
              "demands" -> Json.arr(
                Json.obj(
                  "colour" -> 1.asJson,
                  "paintMix" -> "matte".asJson
                ),
              )
            ),
            Json.obj(
              "customerNumber" -> 2.asJson,
              "demands" -> Json.arr(
                Json.obj(
                  "colour" -> 1.asJson,
                  "paintMix" -> "glossy".asJson
                ),
              )
            )
          )
        )
        for {
          request <- POST(requestEntity, Uri.unsafeFromString(s"v2/optimize"))
          response <- api.run(request)
          _ = response.body.compile.toVector.unsafeRunSync.isEmpty should === (true)
          _ = response.status.code should === (404)
        } yield ()
      }
    ).unsafeRunSync()
  }

  it should "execute the happy path" in new Scope {
    withRoutes(
      routes => {
        val api = routes.orNotFound

        val requestEntity = Json.obj(
          "colours" -> 5.asJson,
          "customers" -> 3.asJson,
          "customerDemands" -> Json.arr(
            Json.obj(
              "customerNumber" -> 1.asJson,
              "demands" -> Json.arr(
                Json.obj(
                  "colour" -> 1.asJson,
                  "paintMix" -> "matte".asJson
                ),
              )
            ),
            Json.obj(
              "customerNumber" -> 2.asJson,
              "demands" -> Json.arr(
                Json.obj(
                  "colour" -> 1.asJson,
                  "paintMix" -> "glossy".asJson
                ),
                Json.obj(
                  "colour" -> 2.asJson,
                  "paintMix" -> "glossy".asJson
                ),
              )
            ),
            Json.obj(
              "customerNumber" -> 3.asJson,
              "demands" -> Json.arr(
                Json.obj(
                  "colour" -> 5.asJson,
                  "paintMix" -> "glossy".asJson
                )
              )
            )
          )
        )

        val expectedResponse = Json.obj(
          "paintMixes" -> Json.arr(
            Json.obj(
              "colour" -> 1.asJson,
              "paintType" -> "Matte".asJson,
            ),
            Json.obj(
              "colour" -> 2.asJson,
              "paintType" -> "Glossy".asJson,
            ),
            Json.obj(
              "colour" -> 3.asJson,
              "paintType" -> "Glossy".asJson,
            ),
            Json.obj(
              "colour" -> 4.asJson,
              "paintType" -> "Glossy".asJson,
            ),
            Json.obj(
              "colour" -> 5.asJson,
              "paintType" -> "Glossy".asJson,
            )
          )
        )

        for {
          request <- POST(requestEntity, Uri.unsafeFromString(s"v2/optimize"))
          response <- api.run(request)
          jsonResponse <- response.as[Json]
          _ = jsonResponse should === (expectedResponse)
          _ = response.status.code should === (200)
        } yield ()
      }
    ).unsafeRunSync()
  }

  trait Scope extends Http4sDsl[IO] with Http4sClientDsl[IO] {
    type F[A] = IO[A]

    val clientConfig = new DefaultAsyncHttpClientConfig.Builder().build()

    val clientR: Resource[F, Client[F]] = AsyncHttpClient.resource[F](clientConfig)

    def withService(testFunc: PaintMixingService[F] => F[Unit]): F[Unit] =
      clientR.use(client => {
        testFunc(new LegacyPaintMixingService[F](client, Uri.unsafeFromString("http://localhost:8081")))
      })

    def withRoutes(httpRoutes: HttpRoutes[F] => F[Unit]): F[Unit] =
      clientR.use(client => {
        val service = new LegacyPaintMixingService[F](client, Uri.unsafeFromString("http://localhost:8081"))
        val paintRestService = PaintRestService(service)
        httpRoutes(PaintApi(paintRestService))
      })
  }
}
