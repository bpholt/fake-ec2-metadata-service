package dev.holt.imds

import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import fs2.*

import java.net.{InetAddress, NetworkInterface}
import scala.jdk.CollectionConverters.*

trait NetworkInterfaces[F[_]] {
  def listInterfaces: Stream[F, NetworkInterface]
  def listIps: Stream[F, InetAddress]

  def guessMyIp: F[Option[IpAddress]]
  def guessMyHostname: F[Option[Host]]
}

object NetworkInterfaces {
  def apply[F[_] : NetworkInterfaces]: NetworkInterfaces[F] = summon

  given[F[_]] (using Sync[F]): NetworkInterfaces[F] with {
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
