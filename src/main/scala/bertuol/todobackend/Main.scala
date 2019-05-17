package bertuol.todobackend

import cats.effect.IO
import cats.effect.Sync
import cats.implicits._
import cats.Monad
import cats.effect.IOApp
import cats.effect.ExitCode
import org.http4s.server.blaze.BlazeServerBuilder

object Main extends IOApp {
  import repository._

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      repo <- inMemoryRepo[IO]()
      service = new Service(repo)
      exitCode <- runProgram(service)
    } yield exitCode
  }

  def runProgram(implicit service: Service[IO]): IO[ExitCode] = {
    val httpApp = APIWebServer[IO].app
    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }
}
