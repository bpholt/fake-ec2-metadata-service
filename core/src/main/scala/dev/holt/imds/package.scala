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

  given Encoder[IpAddress] = Encoder[String].contramap(_.toString)

  given[F[_], A, B](using he: HasExtractor.Aux[B, A])
                   (using EntityEncoder[F, A]): EntityEncoder[F, B] =
    EntityEncoder[F, A].contramap(he.extract)

  given[A, B](using he: HasExtractor.Aux[B, A])
             (using Encoder[A]): Encoder[B] =
    Encoder[A].contramap(he.extract)
}

package imds {
  object IamProfile extends NewtypeWrapped[String]
  object InstanceId extends NewtypeWrapped[String]
  object AmiId extends NewtypeWrapped[String]
  object Region extends NewtypeWrapped[String]
}
