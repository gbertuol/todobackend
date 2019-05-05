package bertuol.todobackend

import cats.effect.IO
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import cats.Monad

object Main extends App {
  import domain._
  import repository._

  val repo = inMemoryRepo[IO]()

  println("Hello World")
}

