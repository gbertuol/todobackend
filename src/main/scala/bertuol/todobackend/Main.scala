package bertuol.todobackend

import cats.effect._
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._
import org.http4s.server.blaze.BlazeServerBuilder
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.chrisdavenport.log4cats.Logger

object Main extends CommandIOApp(
  name = "todobackend",
  header = "backend application for a todo application",
  version = "1.0.0"
) {
  import repository._

  lazy val repoOpts = Opts.flag("in-memory", "use an in-memory repo").orFalse

  override def main: Opts[IO[ExitCode]] = {
    repoOpts.map { case (useInMemoryRepo) => 
      for {
        logger   <- Slf4jLogger.create[IO]
        repo     <- if (useInMemoryRepo) inMemoryRepo[IO]() <* logger.info("creating in-memory repo") 
                    else createRepo(logger) <* logger.info("creating durable repo")
        exitCode <- runProgram(repo)
      } yield exitCode
    }
  }

  def createRepo(implicit logger: Logger[IO]): IO[TodoRepository[IO]] = bootstrapRepo[IO]()

  def runProgram(implicit repo: TodoRepository[IO]): IO[ExitCode] = {
    implicit val service = TodoService.apply[IO]
    val httpApp          = APIWebServer[IO].app
    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }
}
