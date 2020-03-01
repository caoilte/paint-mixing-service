package org.caoilte.paint.domain

trait PaintMixingService[F[_]] {
  def preparationFor(paintMixRequest: PaintMixRequest):F[Option[PaintMixResponse]]
}


final case class PaintMixingServiceError(message: String, throwable: Option[Throwable] = None)
  extends Throwable(throwable.orNull)