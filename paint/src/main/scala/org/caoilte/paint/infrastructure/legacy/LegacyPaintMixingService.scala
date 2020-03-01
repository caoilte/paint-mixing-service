package org.caoilte.paint.infrastructure.legacy

import cats.data.EitherT
import cats.effect.Sync
import io.circe.{Encoder, Printer}
import org.caoilte.paint.domain.{Colour, PaintMix, PaintMixRequest, PaintMixResponse, PaintMixType, PaintMixingService, PaintMixingServiceError}
import org.http4s.{DecodeFailure, DecodeResult, EntityDecoder, MediaRange, MediaType, Message, Request, Uri}
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import io.circe.syntax._
import io.circe.generic.semiauto._
import cats.implicits._


case class LegacyPaintMixingRequest(colors: Int, customers: Int, demands: List[List[Int]])

object LegacyPaintMixingRequest {
  implicit val encoder: Encoder[LegacyPaintMixingRequest] = deriveEncoder
}

class LegacyPaintMixingService[F[_] : Sync](client: Client[F], basePath: Uri) extends PaintMixingService[F] with Http4sDsl[F] with Http4sClientDsl[F] {

  def paintIntsToPaintMixResponse(paintMixes: List[Int]):PaintMixResponse = {
    val mixes = paintMixes.mapWithIndex {
      case (paintTypeNumber, zeroColourIndex) => {
        val paintMixType = if (paintTypeNumber === LegacyPaintMixingService.MATTE) {
          PaintMixType.Matte
        } else PaintMixType.Glossy
        PaintMix(Colour(zeroColourIndex+1), paintMixType)
      }
    }
    PaintMixResponse(mixes)
  }

  implicit val respDec:EntityDecoder[F, Option[PaintMixResponse]] = EntityDecoder.decodeBy(MediaType.text.plain) { m: Message[F] =>
    EitherT {
      m.as[String].map(str => {
        val res:Option[PaintMixResponse] = if (str === "IMPOSSIBLE") {
          Option.empty[PaintMixResponse]
        } else Option(paintIntsToPaintMixResponse(str.split(" ").toList.map(_.toInt)))
        res.asRight[DecodeFailure]
      })
    }
  }

  override def preparationFor(paintMixRequest: PaintMixRequest): F[Option[PaintMixResponse]] = {
    val request = LegacyPaintMixingService.assembleLegacyPaintMixingRequest(paintMixRequest)
    for {
      req: Request[F] <- GET((basePath / "v1" / "").withQueryParam("input", request.asJson.printWith(Printer.noSpaces)))
      response <- client.expect[Option[PaintMixResponse]](req)
    } yield response
  }
}

object LegacyPaintMixingService {
  val GLOSSY = 0
  val MATTE = 1

  def assembleLegacyPaintMixingRequest(paintMixRequest: PaintMixRequest): LegacyPaintMixingRequest = {
    val demands: List[List[Int]] = paintMixRequest.customerLikes.map { c => {
      val matteLikes:List[Int] = c.matteDemands.map(c => List(c.value, MATTE)).toList.flatten
      val glossyLikes:List[Int] = c.glossyDemands.flatMap(c => List(c.value, GLOSSY))
      c.demandsCount :: matteLikes ::: glossyLikes
    }}
    LegacyPaintMixingRequest(
      colors = paintMixRequest.colors,
      customers = paintMixRequest.customerLikes.size,
      demands = demands
    )
  }
}