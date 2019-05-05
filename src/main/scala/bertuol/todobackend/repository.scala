package bertuol.todobackend

import cats.effect.IO
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import cats.Monad

object repository {
  import domain._

  trait TodoRepository[F[_]] {

    def getAll(): F[List[TodoItem]]

    def getById(id: TodoID): F[Option[TodoItem]]

    def delete(id: TodoID): F[Unit]

    def deleteAll(): F[Unit]

    def create(action: CreateTodoItem): F[TodoItem]

    def update(id: TodoID, action: UpdateTodoItem): F[Option[TodoItem]]
  }

  def inMemoryRepo[F[_]: Sync](): F[TodoRepository[F]] = for {
    db <- Ref.of(Map[TodoID, TodoItem]())
    counter <- Ref.of(0L)
    repo = new InMemoryRepo(db, counter)
  } yield repo

  private class InMemoryRepo[F[_]: Monad](db: Ref[F, Map[TodoID, TodoItem]], counter: Ref[F, Long]) extends TodoRepository[F] {

    override def getAll(): F[List[TodoItem]] = db.get.map(_.values.toList)

    override def getById(id: TodoID): F[Option[TodoItem]] = db.get.map(_.get(id))

    override def delete(id: TodoID): F[Unit] = db.update(_ - id)

    override def deleteAll(): F[Unit] = db.update(_.empty)

    override def create(action: CreateTodoItem): F[TodoItem] = for {
      newId <- counter.modify(x => (x + 1, x + 1)).map(TodoID(_))
      newTodoItem = action.todoItem(newId)
      _ <- db.update(_ + (newId -> newTodoItem))
    } yield newTodoItem

    override def update(id: TodoID, action: UpdateTodoItem): F[Option[TodoItem]] = for {
      oldValue <- getById(id)
      result   <- oldValue.fold(Monad[F].pure[Option[TodoItem]](None)) { x =>
        val newValue = action.todoItem(x)
        db.update(_ + (id -> newValue)) *> Monad[F].pure(Some(newValue))
      }
    } yield result
  }
}