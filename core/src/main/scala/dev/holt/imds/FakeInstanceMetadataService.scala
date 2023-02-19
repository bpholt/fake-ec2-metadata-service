package dev.holt.imds

import cats.Monad
import cats.data.OptionT
import cats.effect._
import cats.effect.std._
import cats.syntax.all._
import dev.holt.javatime.literals._
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.circe.jsonEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import shapeless.syntax.std.tuple._

import java.time.temporal.ChronoUnit
import java.time.{Clock => _}
import scala.jdk.OptionConverters._

class FakeInstanceMetadataService[F[_] : Monad : Clock : Env : NetworkInterfaces : AwsCredentials] extends Http4sDsl[F] {
  private val iam: HttpRoutes[F] = Router("iam" -> HttpRoutes.of[F] {
    case GET -> Root / "security-credentials" | GET -> Root / "security-credentials" / "" =>
      OptionT(Env[F].get("AWS_PROFILE"))
        .getOrElse("default")
        .flatMap(Ok(_))

    case GET -> Root / "security-credentials" / ProfileName(name) =>
      OptionT(AwsCredentials[F].loadProfile(name))
        .map(profile => new shapeless.poly.->(profile.property(_: String).toScala))
        .flatMapF { profile =>
          import profile._

          ("aws_access_key_id", "aws_secret_access_key")
            .productElements
            .map(profile)
            .tupled
            .traverseN { case (key: String, secret: String) =>
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
                .map(_.asJson)
                .flatMap(Ok(_))
            }
        }
        .getOrElseF(NotFound(s"could not find $name"))
  })

  private val latestMetaData = iam <+> HttpRoutes.of[F] {
    case GET -> Root / "local-ipv4" =>
      Env[F]
        .get("LOCAL_ADDR")
        .flatMap {
          case Some(addr) => Ok(addr)
          case None =>
            NetworkInterfaces[F]
              .guessMyIp
              .flatMap {
                case Some(ip) => Ok(ip.toString)
                case None => NotFound()
              }
        }

    case GET -> Root / "local-hostname" =>
      NetworkInterfaces[F]
        .guessMyHostname
        .flatMap {
          case Some(host) => Ok(host.toString)
          case None => NotFound()
        }

    case GET -> Root / "instance-id" => Ok("i-local")
    case GET -> Root / "ami-id" => Ok("ami-local")

    case GET -> Root / "placement" / "region" =>
      OptionT(Env[F].get("AWS_DEFAULT_REGION"))
        .orElseF(Env[F].get("AWS_REGION"))
        .getOrElse("us-west-2")
        .flatMap(Ok(_))

  }

  val routes: HttpRoutes[F] = Router("/latest/meta-data" -> latestMetaData)
}

object ProfileName {
  def unapply(s: String): Option[String] =
    Option.when(s.nonEmpty)(s)
}
