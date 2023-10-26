package dev.holt.imds

import cats.*
import cats.effect.*
import cats.effect.std.*
import cats.syntax.all.*
import com.comcast.ip4s.{Host, IpAddress}
import dev.holt.javatime.literals.*
import mouse.all.*
import shapeless.syntax.std.tuple.*

import java.time.temporal.ChronoUnit
import scala.jdk.OptionConverters.*

trait InstanceMetadata[F[_]] {
  def iamProfile: F[IamProfile]
  def securityCredentials(profile: IamProfile): F[Option[SecurityCredentials]]
  def localIpv4: F[Option[IpAddress]]
  def localHostname: F[Option[Host]]
  def instanceId: F[InstanceId]
  def amiId: F[AmiId]
  def region: F[Region]
}

object InstanceMetadata {
  def apply[F[_] : Monad : Clock : Env : NetworkInterfaces : AwsCredentials]: InstanceMetadata[F] = new InstanceMetadata[F] {
    override def iamProfile: F[IamProfile] =
      Env[F].get("AWS_PROFILE")
        .liftOptionT
        .getOrElse("default")
        .map(IamProfile(_))

    override def securityCredentials(profile: IamProfile): F[Option[SecurityCredentials]] =
      AwsCredentials[F].loadProfile(profile.value)
        .liftOptionT
        .flatMapF { profile =>
          ("aws_access_key_id", "aws_secret_access_key")
            .toSized
            .map(profile.property(_).toScala)
            .tupled
            .traverseN { (key: String, secret: String) =>
              Clock[F]
                .realTimeInstant
                .map(_.atZone(zoneId"UTC"))
                .fproduct(_.plus(1, ChronoUnit.HOURS))
                .map { case (l, e) =>
                  SecurityCredentials(
                    LastUpdated = l,
                    AccessKeyId = key,
                    SecretAccessKey = secret,
                    Expiration = e
                  )
                }
            }
        }
        .value

    override def localIpv4: F[Option[IpAddress]] =
      Env[F]
        .get("LOCAL_ADDR")
        .liftOptionT
        .subflatMap(IpAddress.fromString)
        .orElseF(NetworkInterfaces[F].guessMyIp)
        .value

    override def localHostname: F[Option[Host]] =
      NetworkInterfaces[F].guessMyHostname

    override def instanceId: F[InstanceId] =
      InstanceId("i-local").pure[F]

    override def amiId: F[AmiId] =
      AmiId("ami-local").pure[F]

    override def region: F[Region] =
      Env[F].get("AWS_DEFAULT_REGION")
        .liftOptionT
        .orElseF(Env[F].get("AWS_REGION"))
        .getOrElse("us-west-2")
        .map(Region(_))
  }
}
