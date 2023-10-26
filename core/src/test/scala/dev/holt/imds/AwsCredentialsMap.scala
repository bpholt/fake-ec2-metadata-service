package dev.holt.imds

import cats.*
import cats.syntax.all.*
import software.amazon.awssdk.profiles.Profile

import scala.jdk.CollectionConverters.*

object AwsCredentialsMap {
  def apply[F[_] : Applicative](map: Map[String, Map[String, String]]): AwsCredentials[F] = new AwsCredentials[F] {
    override def loadProfiles: F[Map[String, Profile]] = map.map { case (k, v) =>
      k -> Profile.builder().name(k).properties(v.asJava).build()
    }.pure[F]

    override def loadProfile(profile: String): F[Option[Profile]] = loadProfiles.map(_.get(profile))
  }
}
