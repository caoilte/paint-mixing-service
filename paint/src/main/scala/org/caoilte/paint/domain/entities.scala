package org.caoilte.paint.domain

case class CustomerNumber(value: Int)
case class Colour(value: Int)

case class CustomerDemands(customerNumber: CustomerNumber, matteDemands: Option[Colour], glossyDemands:List[Colour]) {
  def demandsCount:Int = matteDemands.size + glossyDemands.size
}

case class PaintMixRequest(colors: Int, customerLikes: List[CustomerDemands])


sealed trait PaintMixType

object PaintMixType {
  case object Matte extends PaintMixType
  case object Glossy extends PaintMixType
}

case class PaintMix(colour: Colour, paintType: PaintMixType)

case class PaintMixResponse(paintMixes: List[PaintMix])