package dev.holt.imds

import cats.effect.*
import cats.syntax.all.*
import software.amazon.awssdk.profiles.{Profile, ProfileFile}

import scala.jdk.CollectionConverters.*

trait AwsCredentials[F[_]] {
  def loadProfiles: F[Map[String, Profile]]
  def loadProfile(profile: String): F[Option[Profile]]
}

object AwsCredentials {
  def apply[F[_] : AwsCredentials]: AwsCredentials[F] = summon

  given [F[_]](using Sync[F]): AwsCredentials[F] with {
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
