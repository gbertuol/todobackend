package bertuol.todobackend

import cats.effect.Effect
import cats.implicits._

object service {
  import domain._
  import repository._

  def createNewTodo[F[_]: Effect](title: String)(implicit repo: TodoRepository[F]): F[TodoItem] =
    for {
      action <- newTodoAction[F](title)
      todo   <- repo.create(action)
    } yield todo

  def updateOrder[F[_]: Effect](id: TodoID, newOrder: Int)(implicit repo: TodoRepository[F]): F[Option[TodoItem]] =
    for {
      action <- updateTodoOrderAction[F](newOrder)
      todo   <- repo.update(id, action)
    } yield todo

  def updateOrder[F[_]: Effect](id: String, newOrder: Int)(implicit repo: TodoRepository[F]): F[Option[TodoItem]] =
    for {
      _id  <- todoId[F](id)
      todo <- updateOrder(_id, newOrder)
    } yield todo

  def getTodoById[F[_]: Effect](id: String)(implicit repo: TodoRepository[F]): F[Option[TodoItem]] =
    for {
      _id  <- todoId[F](id)
      todo <- repo.getById(_id)
    } yield todo

  def getAllTodos[F[_]: Effect]()(implicit repo: TodoRepository[F]): F[List[TodoItem]] =
    repo.getAll()

}
