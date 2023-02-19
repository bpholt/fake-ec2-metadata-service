package dev.holt.imds

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect._
import cats.effect.std.Env
import cats.laws.discipline.arbitrary._
import cats.syntax.all._
import com.comcast.ip4s.Arbitraries._
import com.comcast.ip4s._
import dev.holt.javatime.literals._
import eu.timepit.refined.scalacheck.all._
import eu.timepit.refined.types.all.NonEmptyString
import io.circe._
import io.circe.literal._
import fs2._
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.http4s.Method.GET
import org.http4s.Status.NotFound
import org.http4s.circe.jsonDecoder
import org.http4s.client.UnexpectedStatus
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.syntax.all._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.effect.PropF.forAllF
import org.scalacheck.{Arbitrary, Gen, Shrink}
import software.amazon.awssdk.profiles.Profile

import java.net.{InetAddress, NetworkInterface}
import java.time.Instant
import java.time.format.DateTimeFormatter.ISO_INSTANT
import java.time.temporal.ChronoUnit.HOURS
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class FakeInstanceMetadataServiceSpec
  extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with Http4sClientDsl[IO] {

  private implicit val arbHost: Arbitrary[Host] = Arbitrary {
    Gen.oneOf(
      arbitrary[Hostname],
      arbitrary[IDN],
      arbitrary[IpAddress],
    )
  }

  test("/latest/meta-data/local-ipv4") {
    forAllF { (envAddr: Option[IpAddress], ipAddress: Option[IpAddress]) =>
      implicit val env: Env[IO] = new Env[IO] {
        override def get(name: String): IO[Option[String]] = entries.map(_.toMap.get(name))
        override def entries: IO[immutable.Iterable[(String, String)]] =
          envAddr.map(_.toString).map("LOCAL_ADDR" -> _).toList.pure[IO]
      }

      implicit val networkInterfaces: NetworkInterfaces[IO] = new NoNetworkInterfaces[IO] {
        override def guessMyIp: IO[Option[IpAddress]] = IO.pure(ipAddress)
      }

      val client = org.http4s.client.Client.fromHttpApp(new FakeInstanceMetadataService[IO].routes.orNotFound)

      client.expect[String](GET(uri"/latest/meta-data/local-ipv4"))
        .attempt
        .map { output =>
          envAddr orElse ipAddress match {
            case Some(expected) => assertEquals(output, expected.toString.asRight)
            case None => assertEquals(output, Left(UnexpectedStatus(NotFound, GET, uri"/latest/meta-data/local-ipv4")))
          }
        }
    }
  }

  test("/latest/meta-data/local-hostname") {
    forAllF { host: Option[Host] =>
      implicit val networkInterfaces: NetworkInterfaces[IO] = new NoNetworkInterfaces[IO] {
        override def guessMyHostname: IO[Option[Host]] = IO.pure(host)
      }

      val client = org.http4s.client.Client.fromHttpApp(new FakeInstanceMetadataService[IO].routes.orNotFound)

      client.expect[String](GET(uri"/latest/meta-data/local-hostname"))
        .attempt
        .map { output =>
          host match {
            case Some(expected) => assertEquals(output, expected.toString.asRight)
            case None => assertEquals(output, Left(UnexpectedStatus(NotFound, GET, uri"/latest/meta-data/local-hostname")))
          }
        }
    }
  }

  test("/latest/meta-data/instance-id") {
    implicit val networkInterfaces: NetworkInterfaces[IO] = NoNetworkInterfaces[IO]

    val client = org.http4s.client.Client.fromHttpApp(new FakeInstanceMetadataService[IO].routes.orNotFound)

    client.expect[String](GET(uri"/latest/meta-data/instance-id"))
      .map { output =>
        assertEquals(output, "i-local")
      }
  }

  test("/latest/meta-data/ami-id") {
    implicit val networkInterfaces: NetworkInterfaces[IO] = NoNetworkInterfaces[IO]

    val client = org.http4s.client.Client.fromHttpApp(new FakeInstanceMetadataService[IO].routes.orNotFound)

    client.expect[String](GET(uri"/latest/meta-data/ami-id"))
      .map { output =>
        assertEquals(output, "ami-local")
      }
  }

  test("/latest/meta-data/iam/security-credentials") {
    forAllF { profile: Option[String] =>
      implicit val networkInterfaces: NetworkInterfaces[IO] = NoNetworkInterfaces[IO]
      implicit val env: Env[IO] = new Env[IO] {
        override def get(name: String): IO[Option[String]] =
          profile.filter(_ => name == "AWS_PROFILE").pure[IO]

        override def entries: IO[immutable.Iterable[(String, String)]] = Map.empty.pure[IO]
      }

      val client = org.http4s.client.Client.fromHttpApp(new FakeInstanceMetadataService[IO].routes.orNotFound)

      client.expect[String](GET(uri"/latest/meta-data/iam/security-credentials"))
        .map { output =>
          assertEquals(output, profile.getOrElse("default"))
        }
    }
  }

  test("/latest/meta-data/iam/security-credentials/") {
    forAllF { profile: Option[String] =>
      implicit val networkInterfaces: NetworkInterfaces[IO] = NoNetworkInterfaces[IO]
      implicit val env: Env[IO] = new Env[IO] {
        override def get(name: String): IO[Option[String]] =
          profile.filter(_ => name == "AWS_PROFILE").pure[IO]

        override def entries: IO[immutable.Iterable[(String, String)]] = Map.empty.pure[IO]
      }

      val client = org.http4s.client.Client.fromHttpApp(new FakeInstanceMetadataService[IO].routes.orNotFound)

      client.expect[String](GET(uri"/latest/meta-data/iam/security-credentials/"))
        .map { output =>
          assertEquals(output, profile.getOrElse("default"))
        }
    }
  }

  private implicit def arbProfiles[F[_] : Applicative]: Arbitrary[AwsCredentials[F]] = Arbitrary {
    arbitrary[NonEmptyList[(NonEmptyString, (String, String))]].map { values =>
      AwsCredentialsMap[F](values.toList.map { case (k, (s1, s2)) =>
        k.value -> Map("aws_access_key_id" -> s1, "aws_secret_access_key" -> s2)
      }.toMap)
    }
  }

  private implicit def shrinkProfiles[F[_]]: Shrink[AwsCredentials[F]] = Shrink.shrinkAny

  test("/latest/meta-data/iam/security-credentials/{profile}") {
    forAllF { (creds: AwsCredentials[IO], ts: FiniteDuration) =>
      implicit val networkInterfaces: NetworkInterfaces[IO] = NoNetworkInterfaces[IO]
      implicit val awsCredentials: AwsCredentials[IO] = creds
      implicit val clock: Clock[IO] = new Clock[IO] {
        override def applicative: Applicative[IO] = implicitly
        override def monotonic: IO[FiniteDuration] = IO.pure(ts)
        override def realTime: IO[FiniteDuration] = IO.pure(ts)
      }

      val client = org.http4s.client.Client.fromHttpApp(new FakeInstanceMetadataService[IO].routes.orNotFound)

      creds.loadProfiles.flatMap {
        _.toList.traverse_ { case (k, v) =>
          client.expect[Json](GET(uri"/latest/meta-data/iam/security-credentials" / k))
            .map { output =>
              assertEquals(output,
                json"""{
                "Code": "Success",
                "LastUpdated": ${Instant.EPOCH.plusNanos(ts.toNanos).atZone(zoneId"UTC").format(ISO_INSTANT)},
                "Type": "AWS-HMAC",
                "AccessKeyId": ${v.property("aws_access_key_id").get()},
                "SecretAccessKey": ${v.property("aws_secret_access_key").get()},
                "Expiration": ${Instant.EPOCH.plusNanos(ts.toNanos).plus(1, HOURS).atZone(zoneId"UTC").format(ISO_INSTANT)}
              }""")
            }
        }
      }
    }
  }

  test("/latest/meta-data/placement/region") {
    forAllF { (defaultRegion: Option[String], region: Option[String]) =>
      implicit val env: Env[IO] = new Env[IO] {
        override def get(name: String): IO[Option[String]] =
          entries.map(_.toMap.get(name))

        override def entries: IO[immutable.Iterable[(String, String)]] =
          (defaultRegion.map("AWS_DEFAULT_REGION" -> _) ++
            region.map("AWS_REGION" -> _)).toList.pure[IO]
      }

      val client = org.http4s.client.Client.fromHttpApp(new FakeInstanceMetadataService[IO].routes.orNotFound)

      client.expect[String](GET(uri"/latest/meta-data/placement/region"))
        .map { output =>
          assertEquals(output, defaultRegion.orElse(region).getOrElse("us-west-2"))
        }
    }
  }
}

object NoNetworkInterfaces {
  def apply[F[_] : Applicative]: NetworkInterfaces[F] = new NoNetworkInterfaces[F]
}

class NoNetworkInterfaces[F[_] : Applicative] extends NetworkInterfaces[F] {
  override def listInterfaces: Stream[F, NetworkInterface] = Stream.empty
  override def listIps: Stream[F, InetAddress] = Stream.empty
  override def guessMyIp: F[Option[IpAddress]] = none[IpAddress].pure[F]
  override def guessMyHostname: F[Option[Host]] = none[Host].pure[F]
}

object AwsCredentialsMap {
  def apply[F[_] : Applicative](map: Map[String, Map[String, String]]): AwsCredentials[F] = new AwsCredentials[F] {
    override def loadProfiles: F[Map[String, Profile]] = map.map { case (k, v) =>
      k -> Profile.builder().name(k).properties(v.asJava).build()
    }.pure[F]

    override def loadProfile(profile: String): F[Option[Profile]] = loadProfiles.map(_.get(profile))
  }
}
