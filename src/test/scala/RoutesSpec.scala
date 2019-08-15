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

  implicit val cs = IO.contextShift(ExecutionContext.global)
  implicit val idDecoder = deriveDecoder[TodoID]
  implicit val todoBodyDecoder = deriveDecoder[TodoBody]
  implicit val todoDecoder = deriveDecoder[TodoItem]
  implicit val jsonTodo = jsonOf[IO, TodoItem]

  val api = new APIWebServer[IO]
  val _uri = uri"/todos"

  "Get" should "get the todo" in runTest { service =>
    for {
      todo <- service.createNewTodo("test")
      response <- api.app(service).run(
        Request(method = Method.GET, uri = _uri / todo.id.value)
      )
      result <- response.as[TodoItem]
      _ <- IO(result shouldBe todo)
    } yield ()
  }

  it should "fail if no todo" in runTest { service =>
    for {
      response <- api.app(service).run(
        Request(method = Method.GET, uri = _uri / "foo")
      )
      _ <- IO { response.status shouldBe Status.NotFound }
    } yield ()
  }

  it should "bad request if bad input" in runTest { service =>
    for {
      response <- api.app(service).run(
        Request(method = Method.GET, uri = _uri / "f_f")
      )
      _ <- IO { response.status shouldBe Status.BadRequest }
    } yield ()
  }

  "Post" should "create a new todo" in runTest { service =>
    // IO.unit
    for {
      request <- POST(json"""{"title":"foo"}""", _uri)
      response <- api.app(service).run(request)
      result <- response.as[TodoItem]
      _ <- IO(result.item.title shouldBe "foo")
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