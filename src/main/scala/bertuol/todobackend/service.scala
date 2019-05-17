package bertuol.todobackend

import bertuol.todobackend.repository.TodoRepository
import cats.effect.Effect
import cats.implicits._

class Service[F[_]: Effect](repo: TodoRepository[F]) {
  import domain._

  def createNewTodo(title: String): F[TodoItem] =
    for {
      action <- newTodoAction[F](title)
      todo   <- repo.create(action)
    } yield todo

  def updateOrder(id: TodoID, newOrder: Int): F[Option[TodoItem]] =
    for {
      action <- updateTodoOrderAction[F](newOrder)
      todo   <- repo.update(id, action)
    } yield todo

  def updateOrder(id: String, newOrder: Int): F[Option[TodoItem]] =
    for {
      _id  <- todoId[F](id)
      todo <- updateOrder(_id, newOrder)
    } yield todo

  def updateTitle(id: String, newTitle: String): F[Option[TodoItem]] =
    for {
      _id    <- todoId[F](id)
      action <- updateTodoTitleAction[F](newTitle)
      todo   <- repo.update(_id, action)
    } yield todo

  def updateCompleted(id: String, completed: Boolean): F[Option[TodoItem]] =
    for {
      _id    <- todoId[F](id)
      action <- updateTodoCompletedAction[F](completed)
      todo   <- repo.update(_id, action)
    } yield todo

  def getTodoById(id: String): F[Option[TodoItem]] =
    for {
      _id  <- todoId[F](id)
      todo <- repo.getById(_id)
    } yield todo

  def getAllTodos(): F[List[TodoItem]] =
    repo.getAll()

  def deleteTodo(id: String): F[Unit] =
    for {
      _id <- todoId[F](id)
      _   <- repo.delete(_id)
    } yield ()

  def deleteAllTodos(): F[Unit] =
    repo.deleteAll()

}
