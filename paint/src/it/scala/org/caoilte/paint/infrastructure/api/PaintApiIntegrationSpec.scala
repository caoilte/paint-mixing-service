package org.caoilte.paint.infrastructure.api

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO, Resource, Sync}
import cats.implicits._
import org.http4s.implicits._
import com.whisk.docker.scalatest.DockerTestKit
import io.circe.Json
import io.circe.syntax._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.caoilte.paint.domain._
import org.caoilte.paint.infrastructure.legacy.LegacyPaintDockerKit
import org.http4s.{HttpRoutes, Uri}
import org.http4s.client.Client
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

class PaintApiIntegrationSpec extends AnyFlatSpec with Matchers {

  // make trait
  implicit lazy val ec: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  implicit protected def contextShift: ContextShift[IO] = IO.contextShift(ec)

  // make trait

  behavior of "Paint Mixing API"

  it should "turn a response None into an empty 404" in new Scope {

    val fakePaintMixingService = new PaintMixingService[IO] {
      override def preparationFor(paintMixRequest: PaintMixRequest): IO[Option[PaintMixResponse]] = None.pure[F]
    }

    withAPI(
      fakePaintMixingService,
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

  it should "return a valid response as a 200" in new Scope {

    val fakePaintMixingService = new PaintMixingService[IO] {
      override def preparationFor(paintMixRequest: PaintMixRequest): IO[Option[PaintMixResponse]] =
        Option(
          PaintMixResponse(
            List(
              PaintMix(
                colour = Colour(1),
                paintType = PaintMixType.Matte
              )
            )
          )
        ).pure[IO]
    }
    val expectedResponse = Json.obj(
      "paintMixes" -> Json.arr(
        Json.obj(
          "colour" -> 1.asJson,
          "paintType" -> "Matte".asJson,
        )
      )
    )

    withAPI(
      fakePaintMixingService,
      routes => {
        val api = routes.orNotFound

        val requestEntity = Json.obj(
          "colours" -> 1.asJson,
          "customers" -> 1.asJson,
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

  it should "reject invalid input with useful error messages and return 400" in new Scope {

    val fakePaintMixingService = new PaintMixingService[IO] {
      override def preparationFor(paintMixRequest: PaintMixRequest): IO[Option[PaintMixResponse]] =
        None.pure[IO]
    }

    val emptyRequest = Json.obj(
      "colours" -> 0.asJson,
      "customers" -> 0.asJson,
      "customerDemands" -> Json.arr()
    )
    val emptyRequestResponse = Json.obj(
      "errors" -> Json.arr(
        Json.obj(
          "errorMessage" -> "Number of colours must be 1-2000 but was 0".asJson
        ),
        Json.obj(
          "errorMessage" -> "Number of customers must be 1-2000 but was 0".asJson
        ),
        Json.obj(
          "errorMessage" -> "Number of customer demands must be 1-2000 but was 0".asJson
        )
      )
    )
    withAPI(
      fakePaintMixingService,
      routes => {
        val api = routes.orNotFound

        for {
          request <- POST(emptyRequest, Uri.unsafeFromString(s"v2/optimize"))
          response <- api.run(request)
          jsonResponse <- response.as[Json]
          _ = jsonResponse should === (emptyRequestResponse)
          _ = response.status.code should === (400)
        } yield ()
      }
    ).unsafeRunSync()

    val twoMattesRequest = Json.obj(
      "colours" -> 2.asJson,
      "customers" -> 1.asJson,
      "customerDemands" -> Json.arr(
        Json.obj(
          "customerNumber" -> 1.asJson,
          "demands" -> Json.arr(
            Json.obj(
              "colour" -> 1.asJson,
              "paintMix" -> "matte".asJson
            ),
            Json.obj(
              "colour" -> 2.asJson,
              "paintMix" -> "matte".asJson
            )
          )
        )
      )
    )
    val twoMattesRequestResponse = Json.obj(
      "errors" -> Json.arr(
        Json.obj(
          "errorMessage" -> "Customers are only allowed one Matte colour demand but '1' asked for matte on '1,2'".asJson
        )
      )
    )
    withAPI(
      fakePaintMixingService,
      routes => {
        val api = routes.orNotFound

        for {
          request <- POST(twoMattesRequest, Uri.unsafeFromString(s"v2/optimize"))
          response <- api.run(request)
          jsonResponse <- response.as[Json]
          _ = jsonResponse should === (twoMattesRequestResponse)
          _ = response.status.code should === (400)
        } yield ()
      }
    ).unsafeRunSync()

    val tooManyCustomersRequest = Json.obj(
      "colours" -> 1.asJson,
      "customers" -> 1.asJson,
      "customerDemands" -> Json.arr(
        Json.obj(
          "customerNumber" -> 1.asJson,
          "demands" -> Json.arr(
            Json.obj(
              "colour" -> 1.asJson,
              "paintMix" -> "matte".asJson
            )
          )
        ),
        Json.obj(
          "customerNumber" -> 2.asJson,
          "demands" -> Json.arr(
            Json.obj(
              "colour" -> 1.asJson,
              "paintMix" -> "matte".asJson
            )
          )
        )
      )
    )
    val tooManyCustomersRequestResponse = Json.obj(
      "errors" -> Json.arr(
        Json.obj(
          "errorMessage" -> "Received customer number '2' but customer numbers must be 0-1'".asJson
        )
      )
    )
    withAPI(
      fakePaintMixingService,
      routes => {
        val api = routes.orNotFound

        for {
          request <- POST(tooManyCustomersRequest, Uri.unsafeFromString(s"v2/optimize"))
          response <- api.run(request)
          jsonResponse <- response.as[Json]
          _ = jsonResponse should === (tooManyCustomersRequestResponse)
          _ = response.status.code should === (400)
        } yield ()
      }
    ).unsafeRunSync()


    val tooManyColoursRequest = Json.obj(
      "colours" -> 1.asJson,
      "customers" -> 1.asJson,
      "customerDemands" -> Json.arr(
        Json.obj(
          "customerNumber" -> 1.asJson,
          "demands" -> Json.arr(
            Json.obj(
              "colour" -> 1.asJson,
              "paintMix" -> "matte".asJson
            ),
            Json.obj(
              "colour" -> 2.asJson,
              "paintMix" -> "glossy".asJson
            )
          )
        )
      )
    )
    val tooManyColoursRequestResponse = Json.obj(
      "errors" -> Json.arr(
        Json.obj(
          "errorMessage" -> "Customer '1' had colours '2' but colours must be 1-1'".asJson
        )
      )
    )
    withAPI(
      fakePaintMixingService,
      routes => {
        val api = routes.orNotFound

        for {
          request <- POST(tooManyColoursRequest, Uri.unsafeFromString(s"v2/optimize"))
          response <- api.run(request)
          jsonResponse <- response.as[Json]
          _ = jsonResponse should === (tooManyColoursRequestResponse)
          _ = response.status.code should === (400)
        } yield ()
      }
    ).unsafeRunSync()

    val fakePaintMixesRequest = Json.obj(
      "colours" -> 1.asJson,
      "customers" -> 1.asJson,
      "customerDemands" -> Json.arr(
        Json.obj(
          "customerNumber" -> 1.asJson,
          "demands" -> Json.arr(
            Json.obj(
              "colour" -> 1.asJson,
              "paintMix" -> "premium".asJson
            )
          )
        )
      )
    )
    val fakePaintMixesRequestResponse = Json.obj(
      "errors" -> Json.arr(
        Json.obj(
          "errorMessage" -> "Customer '1' had paint mixes 'premium' but paint mixes must be 'matte' or 'glossy'".asJson
        )
      )
    )
    withAPI(
      fakePaintMixingService,
      routes => {
        val api = routes.orNotFound

        for {
          request <- POST(fakePaintMixesRequest, Uri.unsafeFromString(s"v2/optimize"))
          response <- api.run(request)
          jsonResponse <- response.as[Json]
          _ = jsonResponse should === (fakePaintMixesRequestResponse)
          _ = response.status.code should === (400)
        } yield ()
      }
    ).unsafeRunSync()

  }

  trait Scope extends Http4sDsl[IO] with Http4sClientDsl[IO] {
    type F[A] = IO[A]

    def withAPI(fakePaintMixingService: PaintMixingService[F], httpRoutes: HttpRoutes[F] => F[Unit]): F[Unit] = {
      val paintRestService = PaintRestService(fakePaintMixingService)
      httpRoutes(PaintApi(paintRestService))
    }

  }

}
