package dev.holt.imds

import cats.effect._
import cats.syntax.all._
import software.amazon.awssdk.profiles.{Profile, ProfileFile}

import java.time.{Clock => _}
import scala.jdk.CollectionConverters._

trait AwsCredentials[F[_]] {
  def loadProfiles: F[Map[String, Profile]]
  def loadProfile(profile: String): F[Option[Profile]]
}

object AwsCredentials {
  def apply[F[_] : AwsCredentials]: AwsCredentials[F] = implicitly

  implicit def instance[F[_] : Sync]: AwsCredentials[F] = new AwsCredentials[F] {
    override def loadProfiles: F[Map[String, Profile]] =
      Sync[F].delay {
        ProfileFile
          .defaultProfileFile()
          .profiles()
          .asScala
          .toMap
      }

    override def loadProfile(profile: String): F[Option[Profile]] =
      loadProfiles.map(_.get(profile))
  }
}
