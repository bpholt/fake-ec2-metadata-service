package dev.holt.imds

import cats.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import fs2.*

import java.net.{InetAddress, NetworkInterface}

object NoNetworkInterfaces {
  def apply[F[_] : Applicative]: NetworkInterfaces[F] = new NoNetworkInterfaces[F]
}

class NoNetworkInterfaces[F[_] : Applicative] extends NetworkInterfaces[F] {
  override def listInterfaces: Stream[F, NetworkInterface] = Stream.empty
  override def listIps: Stream[F, InetAddress] = Stream.empty
  override def guessMyIp: F[Option[IpAddress]] = none[IpAddress].pure[F]
  override def guessMyHostname: F[Option[Host]] = none[Host].pure[F]
}
