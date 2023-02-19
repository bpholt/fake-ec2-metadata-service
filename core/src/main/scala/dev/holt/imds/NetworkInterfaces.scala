package dev.holt.imds

import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s._
import fs2._

import java.net.{InetAddress, NetworkInterface}
import java.time.{Clock => _}
import scala.jdk.CollectionConverters._

trait NetworkInterfaces[F[_]] {
  def listInterfaces: Stream[F, NetworkInterface]
  def listIps: Stream[F, InetAddress]

  def guessMyIp: F[Option[IpAddress]]
  def guessMyHostname: F[Option[Host]]
}

object NetworkInterfaces {
  def apply[F[_] : NetworkInterfaces]: NetworkInterfaces[F] = implicitly

  implicit def instance[F[_] : Sync]: NetworkInterfaces[F] = new NetworkInterfaces[F] {
    override def listInterfaces: Stream[F, NetworkInterface] =
      Stream.evalSeq(Sync[F].delay {
        NetworkInterface.getNetworkInterfaces.asScala.toSeq
      })

    override def listIps: Stream[F, InetAddress] =
      listInterfaces
        .flatMap { interface =>
          Stream.evalSeq(Sync[F].delay {
            interface
              .getInetAddresses
              .asScala
              .toSeq
          })
        }

    override def guessMyIp: F[Option[IpAddress]] =
      listIps
        .collectFirst {
          case i if !(i.isLoopbackAddress || i.isLinkLocalAddress || i.isMulticastAddress) =>
            IpAddress.fromInetAddress(i)
        }
        .compile
        .last

    override def guessMyHostname: F[Option[Host]] =
      listIps
        .evalMapFilter {
          case i if !(i.isLoopbackAddress || i.isLinkLocalAddress || i.isMulticastAddress) =>
            Sync[F].delay(Host.fromString(i.getHostName))
          case _ => none[Host].pure[F]
        }
        .head
        .compile
        .last
  }
}
