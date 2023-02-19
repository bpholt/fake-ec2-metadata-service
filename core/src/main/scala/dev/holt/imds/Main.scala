package dev.holt.imds

import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder

import java.time.{Clock => _}

object Main extends ResourceApp.Forever {
  override def run(args: List[String]): Resource[IO, Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port"8169")
      .withHttpApp(new FakeInstanceMetadataService[IO].routes.orNotFound)
      .build
      .void
}
