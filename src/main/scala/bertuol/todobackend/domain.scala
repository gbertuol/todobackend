package bertuol.todobackend
import cats.MonadError

object domain {

  final case class TodoID(value: Long) extends AnyVal
  final case class TodoBody(title: String, completed: Boolean, order: Int)
  final case class TodoItem(id: TodoID, item: TodoBody)
  final case class CreateTodoItem(title: String) {
    def todoItem(id: TodoID): TodoItem = TodoItem(id, TodoBody(title, false, 0))
  }
  final case class UpdateTodoItem(
      title: Option[String],
      completed: Option[Boolean],
      order: Option[Int]
  ) {
    def todoItem(old: TodoItem): TodoItem =
      old.copy(
        item = TodoBody(
          title = title.getOrElse(old.item.title),
          completed = completed.getOrElse(old.item.completed),
          order = order.getOrElse(old.item.order)
        )
      )
  }

  def todoId[F[_]](value: String)(implicit M: MonadError[F, Throwable]): F[TodoID] =
    M.catchNonFatal {
      val _id = value.toLong
      TodoID(_id)
    }

  def newTodoAction[F[_]](title: String)(implicit M: MonadError[F, Throwable]): F[CreateTodoItem] =
    M.pure(CreateTodoItem(title))

  def updateTodoOrderAction[F[_]](
      newOrder: Int
  )(implicit M: MonadError[F, Throwable]): F[UpdateTodoItem] =
    M.pure(UpdateTodoItem(title = None, completed = None, order = Some(newOrder)))

}
