package org.caoilte.paint.infrastructure.api

import cats.effect.{Concurrent, Sync}
import io.chrisdavenport.log4cats.Logger
import org.caoilte.paint.domain.{PaintMixRequest, PaintMixingService}
import org.caoilte.paint.infrastructure.api.PaintRestService.HttpPaintMixRequest
import org.caoilte.paint.infrastructure.api.PaintRestService.HttpPaintMixResponse.{HttpPaintMixNotPossible, HttpSuccessPaintMixesResponse, PaintMixRequestValidationFailure}
import org.http4s.HttpRoutes
import org.http4s.circe.{CirceEntityDecoder, CirceEntityEncoder}
import org.http4s.dsl.Http4sDsl
import cats.implicits._

object PaintApi extends CirceEntityDecoder with CirceEntityEncoder {

  def apply[F[_] : Sync](paintRestService: PaintRestService[F]): HttpRoutes[F] = {
    val http4sDsl = Http4sDsl[F]
    import http4sDsl._

    HttpRoutes.of[F] {
      case req @ POST -> Root / "v2" / "optimize" =>
        req.decodeStrict[HttpPaintMixRequest] { request =>
          (paintRestService.makePaintMixingRequest(request).flatMap {
            case r: PaintMixRequestValidationFailure => BadRequest(r)
            case r: HttpSuccessPaintMixesResponse => Ok(r)
            case HttpPaintMixNotPossible => NotFound()
          }).recoverWith {
            case e: Exception => {
              println(e)
              InternalServerError("Something went wrong")
            }
          }

        }
    }
  }

}
