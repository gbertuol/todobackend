package bertuol.todobackend

import cats.effect.Effect
import cats.effect.ContextShift
import cats.implicits._
import io.finch._
import io.finch.circe._
import io.circe._
import io.circe.generic.auto._
import io.circe.generic.semiauto._

class APIWebServer[F[_]: Effect: ContextShift](implicit service: Service[F]) extends Endpoint.Module[F] {
  import domain._
  import APIWebServer._

  // implicit val errorResponseEncoder           = jsonEncoderOf[F, ErrorResponse]
  implicit val todoEncoder                    = deriveEncoder[TodoItem]
  implicit val createTodoFormDecoder          = deriveDecoder[CreateTodoForm]
  implicit val updateTodoOrderFormDecoder     = deriveDecoder[UpdateTodoOrderForm]
  implicit val updateTodoTitleFormDecoder     = deriveDecoder[UpdateTodoTitleForm]
  implicit val updateTodoCompletedFormDecoder = deriveDecoder[UpdateTodoCompletedForm]

  final val getAllTodos = get(pathEmpty) {
    service.getAllTodos.map(Ok(_))
  }

  final val getTodo = get("todo" :: path[String]) { todoId: String =>
    service.getTodoById(todoId).map(_.map(Ok(_)).getOrElse(NoContent))
  }

  final val createTodo = post("todo" :: jsonBody[CreateTodoForm]) { form: CreateTodoForm =>
    service.createNewTodo(form.title).map(Created(_))
  }

  final val patchTodoOrder = patch("todo" :: path[String] :: "order" :: jsonBody[UpdateTodoOrderForm]) { (todoId: String, form: UpdateTodoOrderForm) =>
    service.updateOrder(todoId, form.order).map(_.map(Ok(_)).getOrElse(NoContent))
  }

  final val patchTodoTitle = patch("todo" :: path[String] :: "title" :: jsonBody[UpdateTodoTitleForm]) { (todoId: String, form: UpdateTodoTitleForm) =>
    service.updateTitle(todoId, form.title).map(_.map(Ok(_)).getOrElse(NoContent))
  }

  final val patchTodoCompleted = patch("todo" :: path[String] :: "completed" :: jsonBody[UpdateTodoCompletedForm]) { (todoId: String, form: UpdateTodoCompletedForm) =>
    service.updateCompleted(todoId, form.completed).map(_.map(Ok(_)).getOrElse(NoContent))
  }

  final val deleteTodo = delete("todo" :: path[String]) { (todoId: String) =>
    service.deleteTodo(todoId).as(Accepted[Unit])
  }

  final val deleteAllTodos = delete("todo" :: pathEmpty) {
    service.deleteAllTodos().as(Accepted[Unit])
  }

  final def toService =
    Bootstrap
      .serve[Application.Json](getAllTodos :+: getTodo :+: createTodo :+: deleteAllTodos :+: deleteTodo :+: patchTodoOrder :+: patchTodoTitle :+: patchTodoCompleted)
      .toService
}

object APIWebServer {

  final case class ErrorResponse(message: Option[String] = None)
  object ErrorResponse {
    def apply(message: String): ErrorResponse = ErrorResponse(Option(message))
  }

  final case class CreateTodoForm(title: String, order: Option[Int])
  final case class UpdateTodoOrderForm(order: Int)
  final case class UpdateTodoTitleForm(title: String)
  final case class UpdateTodoCompletedForm(completed: Boolean)
}
