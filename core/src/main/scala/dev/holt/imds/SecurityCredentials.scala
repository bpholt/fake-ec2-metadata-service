package dev.holt.imds

import io.circe.*

import java.time.format.DateTimeFormatter.ISO_INSTANT
import java.time.{Clock as _, *}

case class SecurityCredentials(Code: String = "Success",
                               LastUpdated: ZonedDateTime,
                               Type: String = "AWS-HMAC",
                               AccessKeyId: String,
                               SecretAccessKey: String,
                               Expiration: ZonedDateTime,
                              ) derives Encoder.AsObject, Decoder

object SecurityCredentials {
  private given Encoder[ZonedDateTime] =
    Encoder[String].contramap(_.format(ISO_INSTANT))
}
