package bertuol.todobackend

import cats.effect.IO
import cats.effect.Sync
import cats.implicits._
import cats.Monad
import cats.effect.IOApp
import cats.effect.ExitCode
import cats.effect.Effect
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.chrisdavenport.log4cats.Logger
import com.twitter.finagle.Http
import io.finch.Endpoint
import com.twitter.util.Await
import cats.effect.Resource
import com.twitter.util.Future
import io.finch.internal.ToAsync

object Main extends IOApp {
  import repository._

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      logger <- Slf4jLogger.create[IO]
      repo   <- createRepo(logger)
      status <- runProgram(repo)
    } yield status
  }

  def createRepo(implicit logger: Logger[IO]): IO[TodoRepository[IO]] =
    inMemoryRepo()
  // bootstrapRepo[IO]()

  def runProgram(implicit repo: TodoRepository[IO]) = {
    implicit val service = new Service[IO]
    val app              = new APIWebServer[IO]()
    val http             = IO(Http.serve(":8080", app.toService))
    val server           = Resource.make(http)(s => IO.suspend(implicitly[ToAsync[Future, IO]].apply(s.close())))
    server.use(_ => IO.never).as(ExitCode.Success)
  }
}
