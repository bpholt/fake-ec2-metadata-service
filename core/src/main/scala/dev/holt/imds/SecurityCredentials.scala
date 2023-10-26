package dev.holt.imds

import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Encoder}

import java.time.format.DateTimeFormatter.ISO_INSTANT
import java.time.{Clock as _, *}
import scala.annotation.nowarn

case class SecurityCredentials(Code: String = "Success",
                               LastUpdated: ZonedDateTime,
                               Type: String = "AWS-HMAC",
                               AccessKeyId: String,
                               SecretAccessKey: String,
                               Expiration: ZonedDateTime,
                              )

object SecurityCredentials {
  @nowarn("msg=private val zonedDateTimeEncoder in object SecurityCredentials is never used")
  private implicit val zonedDateTimeEncoder: Encoder[ZonedDateTime] =
    Encoder[String].contramap(_.format(ISO_INSTANT))

  implicit val securityCredentialsCodec: Codec[SecurityCredentials] = deriveCodec
}
