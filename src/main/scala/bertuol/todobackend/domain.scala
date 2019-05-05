package bertuol.todobackend

object domain {

  final case class TodoID(value: Long) extends AnyVal
  final case class TodoBody(title: String, completed: Boolean, order: Int)
  final case class TodoItem(id: TodoID, item: TodoBody)
  final case class CreateTodoItem(title: String) {
    def todoItem(id: TodoID): TodoItem = TodoItem(id, TodoBody(title, false, 0))
  }
  final case class UpdateTodoItem(title: Option[String], completed: Option[Boolean], order: Option[Int]) {
    def todoItem(old: TodoItem): TodoItem = old.copy(item = TodoBody(
      title = title.getOrElse(old.item.title),
      completed = completed.getOrElse(old.item.completed),
      order = order.getOrElse(old.item.order)
    ))
  }

}