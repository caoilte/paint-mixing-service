package org.caoilte.paint.infrastructure.api

import cats.ApplicativeError
import cats.data.{NonEmptyChain, Validated}
import org.caoilte.paint.domain.{Colour, CustomerDemands, CustomerNumber, PaintMix, PaintMixRequest, PaintMixResponse, PaintMixingService}
import org.caoilte.paint.infrastructure.api.PaintRestService._
import cats.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import org.caoilte.paint.infrastructure.api.PaintRestService.HttpPaintMixResponse.{HttpPaintMix, HttpPaintMixNotPossible, HttpSuccessPaintMixesResponse, PaintMixRequestValidationFailure}
import org.caoilte.paint.infrastructure.legacy.LegacyPaintMixingRequest

trait PaintRestService[F[_]] {
  def makePaintMixingRequest(httpPaintMixRequest: HttpPaintMixRequest): F[HttpPaintMixResponse]
}

object PaintRestService {
  val MATTE = "matte"
  val GLOSSY = "glossy"
  case class HttpCustomerDemand(colour: Int, paintMix: String)
  case class HttpCustomerDemands(customerNumber: Int, demands: List[HttpCustomerDemand])
  case class HttpPaintMixRequest(colours: Int, customers: Int, customerDemands: List[HttpCustomerDemands])

  object HttpPaintMixRequest {
    implicit val httpCustomerDemandEncoder: Decoder[HttpCustomerDemand] = deriveDecoder
    implicit val httpCustomerDemandsEncoder: Decoder[HttpCustomerDemands] = deriveDecoder
    implicit val decoder: Decoder[HttpPaintMixRequest] = deriveDecoder
  }

  type ValidatedPaintRequest[T] = Validated[NonEmptyChain[GeneralError], T]

  def validatedMatteColour(customerDemands: HttpCustomerDemands): ValidatedPaintRequest[Option[Colour]] = {
    import customerDemands._
    val matteLikes = demands.filter(_.paintMix == MATTE)
    if (matteLikes.size <= 1) matteLikes.headOption.map(matteDemand => Colour(matteDemand.colour)).validNec
    else
      GeneralError(
        s"Customers are only allowed one Matte colour demand but '$customerNumber' asked for matte on '${matteLikes.map(_.colour).mkString(",")}'"
      ).invalidNec
  }

  def validateCustomerNumber(maxValidCustomerNumber: Int, customerDemands: HttpCustomerDemands): ValidatedPaintRequest[Unit] = {
    import customerDemands._
    if (customerNumber > 0 && customerNumber <= maxValidCustomerNumber) {
      ().validNec
    } else GeneralError(s"Received customer number '$customerNumber' but customer numbers must be 0-$maxValidCustomerNumber'").invalidNec
  }

  def validateColours(maxValidColour: Int, customerDemands: HttpCustomerDemands): ValidatedPaintRequest[Unit] = {
    import customerDemands._
    val colours = customerDemands.demands.map(_.colour)
    val invalidColours = colours.filter(c => c < 1 || c > maxValidColour)
    if (invalidColours.isEmpty) ().validNec
    else
      GeneralError(s"Customer '$customerNumber' had colours '${invalidColours.mkString(",")}' but colours must be 1-$maxValidColour'").invalidNec
  }

  def validatePaintMixes(customerDemands: HttpCustomerDemands): ValidatedPaintRequest[Unit] = {
    import customerDemands._
    val invalidPaintMixes = customerDemands.demands.filter(d => d.paintMix != MATTE && d.paintMix != GLOSSY)
    if (invalidPaintMixes.isEmpty) ().validNec
    else
      GeneralError(
        s"Customer '$customerNumber' had paint mixes '${invalidPaintMixes.map(_.paintMix).mkString(",")}' but paint mixes must be '$MATTE' or '$GLOSSY'"
      ).invalidNec
  }

  def validatedCustomerDemands(
      maxValidColour: Int,
      maxValidCustomerNumber: Int,
      customerDemands: HttpCustomerDemands
    ): ValidatedPaintRequest[CustomerDemands] =
    (
      validatedMatteColour(customerDemands),
      validateCustomerNumber(maxValidCustomerNumber, customerDemands),
      validateColours(maxValidColour, customerDemands),
      validatePaintMixes(customerDemands)
    ).mapN {
      case (matteColour, _, _, _) =>
        CustomerDemands(
          CustomerNumber(customerDemands.customerNumber),
          matteColour,
          customerDemands.demands.filter(_.paintMix == GLOSSY).map(c => Colour(c.colour))
        )
    }

  def validateNumberOfColours(colours: Int): ValidatedPaintRequest[Unit] =
    if (colours > 0 && colours <= 2000) ().validNec
    else GeneralError(s"Number of colours must be 1-2000 but was $colours").invalidNec

  def validateNumberOfCustomers(customers: Int): ValidatedPaintRequest[Unit] =
    if (customers > 0 && customers <= 2000) ().validNec
    else GeneralError(s"Number of customers must be 1-2000 but was $customers").invalidNec

  def validateNumberOfDemands(customerDenands: List[HttpCustomerDemands]): ValidatedPaintRequest[Unit] = {
    val totalCustomerDemandsCount: Int = customerDenands.map(cd => cd.demands.size).sum
    if (totalCustomerDemandsCount > 0 && totalCustomerDemandsCount <= 2000) ().validNec
    else GeneralError(s"Number of customer demands must be 1-2000 but was $totalCustomerDemandsCount").invalidNec
  }

  def validatePaintMixRequest(httpPaintMixRequest: HttpPaintMixRequest): ValidatedPaintRequest[PaintMixRequest] = {
    import httpPaintMixRequest._
    (
      customerDemands.map(validatedCustomerDemands(colours, customers, _)).sequence,
      validateNumberOfColours(colours),
      validateNumberOfCustomers(customers),
      validateNumberOfDemands(customerDemands)
    ).mapN {
      case (validCustomerDemands, _, _, _) =>
        PaintMixRequest(colours, validCustomerDemands)
    }
  }

  def toHttpPaintMix(paintMix: PaintMix): HttpPaintMix =
    HttpPaintMix(
      colour = paintMix.colour.value,
      paintType = paintMix.paintType.toString
    )

  def apply[F[_]](paintMixingService: PaintMixingService[F])(implicit err: ApplicativeError[F, Throwable]): PaintRestService[F] =
    (httpPaintMixRequest: HttpPaintMixRequest) => {
      validatePaintMixRequest(httpPaintMixRequest).fold(
        e => PaintMixRequestValidationFailure(e).pure[F].widen,
        r => {
          paintMixingService
            .preparationFor(r)
            .map(
              maybeResponse =>
                maybeResponse
                  .fold[HttpPaintMixResponse](HttpPaintMixNotPossible)((r: PaintMixResponse) => HttpSuccessPaintMixesResponse(r.paintMixes.map(toHttpPaintMix)))
            )
        }
      )

    }

  sealed trait HttpPaintMixResponse

  object HttpPaintMixResponse {
    case class PaintMixRequestValidationFailure(errors: NonEmptyChain[GeneralError]) extends HttpPaintMixResponse

    object PaintMixRequestValidationFailure {
      implicit val generalErrorEncoder: Encoder[GeneralError] = deriveEncoder
      implicit val encoder: Encoder[PaintMixRequestValidationFailure] = deriveEncoder
    }

    case class HttpPaintMix(colour: Int, paintType: String)
    case class HttpSuccessPaintMixesResponse(paintMixes: List[HttpPaintMix]) extends HttpPaintMixResponse

    object HttpSuccessPaintMixesResponse {
      implicit val httpPaintMixEncoder: Encoder[HttpPaintMix] = deriveEncoder
      implicit val encoder: Encoder[HttpSuccessPaintMixesResponse] = deriveEncoder
    }

    case object HttpPaintMixNotPossible extends HttpPaintMixResponse
  }

  case class GeneralError(errorMessage: String)
}
