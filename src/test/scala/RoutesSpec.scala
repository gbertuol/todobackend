import org.scalatest._
import bertuol.todobackend.repository._
import bertuol.todobackend.domain._
import cats.effect.IO
import cats.implicits._
import bertuol.todobackend.APIWebServer
import scala.concurrent.ExecutionContext
import bertuol.todobackend.Service
import io.circe._
import io.circe.literal._
import io.circe.syntax._
import io.circe.generic.semiauto._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.client._
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.implicits._

class RoutesSpec extends FlatSpec with Matchers with Inside {

  implicit val cs              = IO.contextShift(ExecutionContext.global)
  implicit val idDecoder       = deriveDecoder[TodoID]
  implicit val todoBodyDecoder = deriveDecoder[TodoBody]
  implicit val todoDecoder     = deriveDecoder[TodoItem]
  implicit val jsonTodo        = jsonOf[IO, TodoItem]

  val api  = new APIWebServer[IO]
  val _uri = uri"/todos"

  "API" should "get the todo by id" in runTest { service =>
    for {
      todo <- service.createNewTodo("test")
      request <- GET(_uri / todo.id.value)
      response <- api.app(service).run(request)
      result <- response.as[TodoItem]
      _      <- IO(result shouldBe todo)
    } yield ()
  }

  it should "fail to get by id if no todo" in runTest { service =>
    for {
      request <- GET(_uri / "foo")
      response <- api.app(service).run(request)
      _ <- IO { response.status shouldBe Status.NotFound }
    } yield ()
  }

  it should "fail to get by id if bad input" in runTest { service =>
    for {
      request <- GET(_uri / "f_f")
      response <- api.app(service).run(request)
      _ <- IO { response.status shouldBe Status.BadRequest }
    } yield ()
  }

  it should "create a new todo" in runTest { service =>
    for {
      request  <- POST(json"""{"title":" My Title Name 1223 "}""", _uri)
      response <- api.app(service).run(request)
      result   <- response.as[TodoItem]
      _        <- IO(result.item.title shouldBe " My Title Name 1223 ")
    } yield ()
  }

  it should "create if invalid name" in runTest { service =>
    for {
      request  <- POST(json"""{"title":"f%o?"}""", _uri)
      response <- api.app(service).run(request)
      _        <- IO(response.status shouldBe Status.BadRequest)
    } yield ()
  }

  it should "update the todo order" in runTest { service =>
    for {
      todo     <- service.createNewTodo("foo")
      request  <- PATCH(json"""{"order":1}""", _uri / todo.id.value / "order")
      response <- api.app(service).run(request)
      result   <- response.as[TodoItem]
      _        <- IO(result.item.order shouldBe 1)
    } yield ()
  }

  it should "fail updating order of non-existing todo" in runTest { service =>
    for {
      request  <- PATCH(json"""{"order":1}""", _uri / "foo" / "order")
      response <- api.app(service).run(request)
      _        <- IO(response.status shouldBe Status.NotFound)
    } yield ()
  }

  def runTest(t: Service[IO] => IO[Unit]): Unit = {
    val test = for {
      repo <- inMemoryRepo[IO]()
      service = createService(repo)
      _ <- t(service)
    } yield ()

    test.unsafeRunSync()
  }

  def createService(implicit repo: TodoRepository[IO]): Service[IO] = new Service[IO]
}
