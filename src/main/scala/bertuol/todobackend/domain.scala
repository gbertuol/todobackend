package bertuol.todobackend

import cats.MonadError
import cats.Monad
import java.{util => ju}
import cats.effect.Sync

object domain {

  final case class TodoID(value: String) extends AnyVal
  final case class TodoBody(title: String, completed: Boolean, order: Int)
  final case class TodoItem(id: TodoID, item: TodoBody)

  final case class CreateTodoItem(title: String) {
    def todoItem(id: TodoID): TodoItem = TodoItem(id, TodoBody(title, false, 0))
  }
  final case class UpdateTodoItem(title: Option[String], completed: Option[Boolean], order: Option[Int]) {
    def todoItem(old: TodoItem): TodoItem =
      old.copy(
        item = TodoBody(
          title = title.getOrElse(old.item.title),
          completed = completed.getOrElse(old.item.completed),
          order = order.getOrElse(old.item.order)
        )
      )
  }

  object TodoID {
    import cats.syntax.functor._

    val validId = """^([a-zA-Z0-9\-])+$""".r

    def parse[F[_]: Sync](_id: String): F[TodoID] =
      _id match {
        case validId(_*) => Sync[F].pure(TodoID(_id))
        case _ => Sync[F].raiseError(new IllegalArgumentException("Invalid todo id"))
      }

    def random[F[_]: Sync]: F[TodoID] =
      Sync[F].delay(ju.UUID.randomUUID.toString).map(TodoID(_))
  }

  object CreateTodoItem {
    def apply[F[_]](title: String)(implicit M: MonadError[F, Throwable]): F[CreateTodoItem] =
      M.pure(CreateTodoItem(title))
  }

  object UpdateTodoItem {

    def updateOrder[F[_]](newOrder: Int)(implicit M: MonadError[F, Throwable]): F[UpdateTodoItem] =
      if (newOrder < 0) {
        M.raiseError(new IllegalArgumentException(s"a todo order must not be a negative number: $newOrder"))
      } else {
        M.pure(UpdateTodoItem(title = None, completed = None, order = Some(newOrder)))
      }

    def updateTitle[F[_]](newTitle: String)(implicit M: MonadError[F, Throwable]): F[UpdateTodoItem] =
      if (newTitle.size > 20) {
        M.raiseError(new IllegalArgumentException(s"the length of a todo title must not be greater than 20"))
      } else {
        M.pure(UpdateTodoItem(title = Some(newTitle), completed = None, order = None))
      }

    def updateCompleted[F[_]: Monad](completed: Boolean): F[UpdateTodoItem] =
      Monad[F].pure(UpdateTodoItem(title = None, completed = Some(completed), order = None))
  }

}
