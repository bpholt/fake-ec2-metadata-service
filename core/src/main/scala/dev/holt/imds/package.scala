package dev.holt

import com.comcast.ip4s.IpAddress
import io.circe.Encoder
import monix.newtypes.*
import org.http4s.EntityEncoder

package object imds {
  type IamProfile = IamProfile.Type
  type InstanceId = InstanceId.Type
  type AmiId = AmiId.Type
  type Region = Region.Type

  implicit val ipAddressEncoder: Encoder[IpAddress] = Encoder[String].contramap(_.toString)

  implicit def newtypeEntityEncoder[F[_], A, B](implicit he: HasExtractor.Aux[B, A],
                                                ee: EntityEncoder[F, A],
                                               ): EntityEncoder[F, B] =
    EntityEncoder[F, A].contramap(he.extract)

  implicit def newtypeJsonEncoder[A, B](implicit he: HasExtractor.Aux[B, A],
                                        e: Encoder[A]): Encoder[B] =
    Encoder[A].contramap(he.extract)
}

package imds {
  object IamProfile extends NewtypeWrapped[String]
  object InstanceId extends NewtypeWrapped[String]
  object AmiId extends NewtypeWrapped[String]
  object Region extends NewtypeWrapped[String]
}
