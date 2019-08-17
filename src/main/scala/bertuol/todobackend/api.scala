package bertuol.todobackend

import cats.effect.Effect
import cats.effect.ContextShift
import cats.implicits._
import io.circe.syntax._
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.server.middleware.CORS
import org.http4s.syntax.kleisli._

class APIWebServer[F[_]](implicit eff: Effect[F], cs: ContextShift[F]) extends Http4sDsl[F] {
  import domain._
  import APIWebServer._

  implicit val errorResponseEncoder           = jsonEncoderOf[F, ErrorResponse]
  implicit val todoEncoder                    = jsonEncoderOf[F, TodoItem]
  implicit val todoListEncoder                = jsonEncoderOf[F, List[TodoItem]]
  implicit val createTodoFormDecoder          = jsonOf[F, CreateTodoForm]
  implicit val updateTodoOrderFormDecoder     = jsonOf[F, UpdateTodoOrderForm]
  implicit val updateTodoTitleFormDecoder     = jsonOf[F, UpdateTodoTitleForm]
  implicit val updateTodoCompletedFormDecoder = jsonOf[F, UpdateTodoCompletedForm]

  def app(implicit service: Service[F]): HttpApp[F]       = CORS(routes.orNotFound)
  def routes(implicit service: Service[F]): HttpRoutes[F] = rootRoutes

  private def rootRoutes(implicit service: Service[F]): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET -> Root =>
        service.getAllTodos().attempt.flatMap {
          case Left(ex) => BadRequest(ErrorResponse(ex.getMessage))
          case Right(v) => Ok(v)
        }

      case GET -> Root / "todos" / todoId =>
        service.getTodoById(todoId).attempt.flatMap {
          case Left(ex)       => BadRequest(ErrorResponse(ex.getMessage))
          case Right(Some(v)) => Ok(v)
          case Right(None)    => NotFound()
        }

      case req @ POST -> Root / "todos" =>
        for {
          form <- req.as[CreateTodoForm]
          resp <- service.createNewTodo(form.title).attempt.flatMap {
            case Left(ex) => BadRequest(ErrorResponse(ex.getMessage))
            case Right(v) => Created(v)
          }
        } yield resp

      case req @ PATCH -> Root / "todos" / todoId / "order" =>
        for {
          form <- req.as[UpdateTodoOrderForm]
          resp <- service.updateOrder(todoId, form.order).attempt.flatMap {
            case Left(ex)       => BadRequest(ErrorResponse(ex.getMessage))
            case Right(Some(v)) => Ok(v)
            case Right(None)    => NotFound()
          }
        } yield resp

      case req @ PATCH -> Root / "todos" / todoId / "title" =>
        for {
          form <- req.as[UpdateTodoTitleForm]
          resp <- service.updateTitle(todoId, form.title).attempt.flatMap {
            case Left(ex)       => BadRequest(ErrorResponse(ex.getMessage))
            case Right(Some(v)) => Ok(v)
            case Right(None)    => NotFound()
          }
        } yield resp

      case req @ PATCH -> Root / "todos" / todoId / "completed" =>
        for {
          form <- req.as[UpdateTodoCompletedForm]
          resp <- service.updateCompleted(todoId, form.completed).attempt.flatMap {
            case Left(ex)       => BadRequest(ErrorResponse(ex.getMessage))
            case Right(Some(v)) => Ok(v)
            case Right(None)    => NotFound()
          }
        } yield resp

      case DELETE -> Root / "todos" / todoId =>
        service.deleteTodo(todoId).attempt.flatMap {
          case Left(ex) => BadRequest(ErrorResponse(ex.getMessage))
          case Right(_) => Ok()
        }

      case DELETE -> Root / "todos" =>
        service.deleteAllTodos() *> Ok()
    }

}

object APIWebServer {

  def apply[F[_]: Effect: ContextShift]: APIWebServer[F] = new APIWebServer[F]

  final case class ErrorResponse(message: Option[String] = None)
  object ErrorResponse {
    def apply(message: String): ErrorResponse = ErrorResponse(Option(message))
  }

  final case class CreateTodoForm(title: String)
  final case class UpdateTodoOrderForm(order: Int)
  final case class UpdateTodoTitleForm(title: String)
  final case class UpdateTodoCompletedForm(completed: Boolean)
}
