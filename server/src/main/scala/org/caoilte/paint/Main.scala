package org.caoilte.paint

import java.util.concurrent.Executors

import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.http4s.client.Client
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.server.blaze.BlazeServerBuilder
import cats.implicits._
import org.caoilte.paint.infrastructure.api.{PaintApi, PaintRestService}
import org.caoilte.paint.infrastructure.legacy.LegacyPaintMixingService
import org.http4s.Uri
import org.http4s.server.Server
import org.http4s.syntax.kleisli._
import fs2.Stream

object Main extends IOApp {

  // $COVERAGE-OFF$
  override def run(args: List[String]): IO[ExitCode] = {
    val clientConfig = new DefaultAsyncHttpClientConfig.Builder().build()

    val appStream: Stream[IO, ExitCode] = for {
      client <- AsyncHttpClient.stream[IO](clientConfig)
      service = new LegacyPaintMixingService[IO](client, Uri.unsafeFromString("http://localhost:8080"))
      paintRestService = PaintRestService(service)
      serverBuild = BlazeServerBuilder[IO].withNio2(true).bindHttp(9090, "0.0.0.0")
      httpApp = PaintApi(paintRestService).orNotFound
      _ = println("App Started")
      exitCode <- serverBuild.withHttpApp(httpApp).serve
      _ = println("App Shutting Down")
    } yield exitCode
    appStream.compile.lastOrError.as(ExitCode.Success) //.drain.as(ExitCode.Success)
  }
  // $COVERAGE-ON$

}
