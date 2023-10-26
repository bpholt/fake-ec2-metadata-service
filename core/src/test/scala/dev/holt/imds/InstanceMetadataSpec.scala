package dev.holt.imds

import cats.*
import cats.data.NonEmptyList
import cats.effect.*
import cats.effect.std.Env
import cats.laws.discipline.arbitrary.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import com.comcast.ip4s.Arbitraries.*
import dev.holt.imds.JavaOptionalInstances.*
import dev.holt.javatime.literals.*
import eu.timepit.refined.scalacheck.all.*
import eu.timepit.refined.types.all.NonEmptyString
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.effect.PropF.forAllF
import org.scalacheck.{Arbitrary, Gen, Shrink}

import java.time.Instant
import java.time.temporal.ChronoUnit.HOURS
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import scala.jdk.OptionConverters.*

class InstanceMetadataSpec
  extends CatsEffectSuite
    with ScalaCheckEffectSuite {

  private implicit val arbHost: Arbitrary[Host] = Arbitrary {
    Gen.oneOf(
      arbitrary[Hostname],
      arbitrary[IDN],
      arbitrary[IpAddress],
    )
  }

  test("Local IPv4") {
    forAllF { (envAddr: Option[IpAddress], ipAddress: Option[IpAddress]) =>
      implicit val env: Env[IO] = new Env[IO] {
        override def get(name: String): IO[Option[String]] = entries.map(_.toMap.get(name))
        override def entries: IO[immutable.Iterable[(String, String)]] =
          envAddr.map(_.toString).map("LOCAL_ADDR" -> _).toList.pure[IO]
      }

      implicit val networkInterfaces: NetworkInterfaces[IO] = new NoNetworkInterfaces[IO] {
        override def guessMyIp: IO[Option[IpAddress]] = IO.pure(ipAddress)
      }

      InstanceMetadata[IO]
        .localIpv4
        .map(assertEquals(_, envAddr orElse ipAddress))
    }
  }

  test("Local Hostname") {
    forAllF { host: Option[Host] =>
      implicit val networkInterfaces: NetworkInterfaces[IO] = new NoNetworkInterfaces[IO] {
        override def guessMyHostname: IO[Option[Host]] = IO.pure(host)
      }

      InstanceMetadata[IO]
        .localHostname
        .map(assertEquals(_, host))
    }
  }

  test("Instance ID") {
    implicit val networkInterfaces: NetworkInterfaces[IO] = NoNetworkInterfaces[IO]

    InstanceMetadata[IO]
      .instanceId
      .map(assertEquals(_, InstanceId("i-local")))
  }

  test("AMI ID") {
    implicit val networkInterfaces: NetworkInterfaces[IO] = NoNetworkInterfaces[IO]

    InstanceMetadata[IO]
      .amiId
      .map(assertEquals(_, AmiId("ami-local")))
  }

  test("IAM Profile") {
    forAllF { profile: Option[String] =>
      implicit val networkInterfaces: NetworkInterfaces[IO] = NoNetworkInterfaces[IO]
      implicit val env: Env[IO] = new Env[IO] {
        override def get(name: String): IO[Option[String]] =
          profile.filter(_ => name == "AWS_PROFILE").pure[IO]

        override def entries: IO[immutable.Iterable[(String, String)]] = Map.empty.pure[IO]
      }

      InstanceMetadata[IO]
        .iamProfile
        .map(assertEquals(_, IamProfile(profile.getOrElse("default"))))
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

  test("Security Credentials for Profile") {
    forAllF { (creds: AwsCredentials[IO], ts: FiniteDuration) =>
      implicit val networkInterfaces: NetworkInterfaces[IO] = NoNetworkInterfaces[IO]
      implicit val awsCredentials: AwsCredentials[IO] = creds
      implicit val clock: Clock[IO] = new Clock[IO] {
        override def applicative: Applicative[IO] = implicitly
        override def monotonic: IO[FiniteDuration] = IO.pure(ts)
        override def realTime: IO[FiniteDuration] = IO.pure(ts)
      }

      val instanceMetadata = InstanceMetadata[IO]

      creds.loadProfiles.flatMap {
        _.toList.traverse_ { case (k, v) =>
          val expected =
            (v.property("aws_access_key_id"), v.property("aws_secret_access_key")).mapN {
              SecurityCredentials(
                "Success",
                Instant.EPOCH.plusNanos(ts.toNanos).atZone(zoneId"UTC"),
                "AWS-HMAC",
                _,
                _,
                Instant.EPOCH.plusNanos(ts.toNanos).plus(1, HOURS).atZone(zoneId"UTC")
              )
            }.toScala

          instanceMetadata.securityCredentials(IamProfile(k))
            .map(assertEquals(_, expected))
        }
      }
    }
  }

  test("Region") {
    forAllF { (defaultRegion: Option[String], region: Option[String]) =>
      implicit val env: Env[IO] = new Env[IO] {
        override def get(name: String): IO[Option[String]] =
          entries.map(_.toMap.get(name))

        override def entries: IO[immutable.Iterable[(String, String)]] =
          (defaultRegion.map("AWS_DEFAULT_REGION" -> _) ++
            region.map("AWS_REGION" -> _)).toList.pure[IO]
      }

      InstanceMetadata[IO]
        .region
        .map(assertEquals(_, Region(defaultRegion.orElse(region).getOrElse("us-west-2"))))
    }
  }
}
