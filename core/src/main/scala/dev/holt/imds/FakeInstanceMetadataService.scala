package dev.holt.imds

import cats.effect.*
import cats.syntax.all.*
import io.circe.syntax.*
import mouse.all.*
import org.http4s.HttpRoutes
import org.http4s.circe.jsonEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class FakeInstanceMetadataService[F[_] : Concurrent](instanceMetadata: InstanceMetadata[F]) extends Http4sDsl[F] {

  private val iam: HttpRoutes[F] = Router("iam" -> HttpRoutes.of[F] {
    case GET -> Root / "security-credentials" | GET -> Root / "security-credentials" / "" =>
      instanceMetadata.iamProfile.flatMap(Ok(_))

    case GET -> Root / "security-credentials" / ProfileName(name) =>
      instanceMetadata.securityCredentials(name)
        .liftOptionT
        .map(_.asJson)
        .semiflatMap(Ok(_))
        .getOrElseF(NotFound(s"could not find $name"))
  })

  private val latestMetaData = iam <+> HttpRoutes.of[F] {
    case GET -> Root / "local-ipv4" =>
      instanceMetadata.localIpv4
        .flatMap {
          case Some(ip) => Ok(ip.toString)
          case None => NotFound()
        }

    case GET -> Root / "local-hostname" =>
      instanceMetadata.localHostname
        .flatMap {
          case Some(host) => Ok(host.toString)
          case None => NotFound()
        }

    case GET -> Root / "instance-id" =>
      instanceMetadata.instanceId.flatMap(Ok(_))

    case GET -> Root / "ami-id" =>
      instanceMetadata.amiId.flatMap(Ok(_))

    case GET -> Root / "placement" / "region" =>
      instanceMetadata.region.flatMap(Ok(_))

  }

  val routes: HttpRoutes[F] = Router("/latest/meta-data" -> latestMetaData)
}

object ProfileName {
  def unapply(s: String): Option[IamProfile] =
    Option.when(s.nonEmpty)(s).map(IamProfile(_))
}
