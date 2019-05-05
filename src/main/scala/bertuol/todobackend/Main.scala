package bertuol.todobackend

import cats.effect.IO
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.Monad
import cats.effect.IOApp
import cats.effect.ExitCode

object Main extends IOApp {
  import domain._
  import repository._
  import service._

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      repo     <- inMemoryRepo[IO]()
      exitCode <- runProgram(repo)
    } yield exitCode
  }

  def runProgram(implicit repo: TodoRepository[IO]): IO[ExitCode] = {
    for {
      foo        <- createNewTodo[IO]("foo")
      bar        <- createNewTodo[IO]("bar")
      _          <- IO { println(foo) }
      _          <- IO { println(bar) }
      updatedBar <- updateOrder[IO](bar.id, 1)
      _          <- IO { updatedBar.foreach(println) }
    } yield ExitCode.Success
  }
}
