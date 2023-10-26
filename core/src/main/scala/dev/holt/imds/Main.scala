package dev.holt.imds

import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger

object Main extends ResourceApp.Forever {
  override def run(args: List[String]): Resource[IO, Unit] = {
    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port"8169")
      .withHttpApp(Logger.httpApp(logHeaders = true, logBody = false) {
        new FakeInstanceMetadataService(InstanceMetadata[IO]).routes.orNotFound
      })
      .build
      .void
  }
}
