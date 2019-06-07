package bertuol.todobackend

import bertuol.todobackend.repository.TodoRepository
import cats.effect.Effect
import cats.implicits._

class Service[F[_]: Effect: TodoRepository] {
  import domain._

  def createNewTodo(title: String): F[TodoItem] =
    for {
      action <- CreateTodoItem[F](title)
      todo   <- TodoRepository[F].create(action)
    } yield todo

  def updateOrder(id: TodoID, newOrder: Int): F[Option[TodoItem]] =
    for {
      action <- UpdateTodoItem.updateOrder[F](newOrder)
      todo   <- TodoRepository[F].update(id, action)
    } yield todo

  def updateOrder(id: String, newOrder: Int): F[Option[TodoItem]] =
    for {
      _id  <- TodoID[F](id)
      todo <- updateOrder(_id, newOrder)
    } yield todo

  def updateTitle(id: String, newTitle: String): F[Option[TodoItem]] =
    for {
      _id    <- TodoID[F](id)
      action <- UpdateTodoItem.updateTitle[F](newTitle)
      todo   <- TodoRepository[F].update(_id, action)
    } yield todo

  def updateCompleted(id: String, completed: Boolean): F[Option[TodoItem]] =
    for {
      _id    <- TodoID[F](id)
      action <- UpdateTodoItem.updateCompleted[F](completed)
      todo   <- TodoRepository[F].update(_id, action)
    } yield todo

  def getTodoById(id: String): F[Option[TodoItem]] =
    for {
      _id  <- TodoID[F](id)
      todo <- TodoRepository[F].getById(_id)
    } yield todo

  def getAllTodos(): F[List[TodoItem]] =
    TodoRepository[F].getAll()

  def deleteTodo(id: String): F[Unit] =
    for {
      _id <- TodoID[F](id)
      _   <- TodoRepository[F].delete(_id)
    } yield ()

  def deleteAllTodos(): F[Unit] =
    TodoRepository[F].deleteAll()

}
