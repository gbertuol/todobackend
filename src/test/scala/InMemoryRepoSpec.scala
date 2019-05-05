
import org.scalatest._
import bertuol.todobackend.repository._
import bertuol.todobackend.domain._
import cats.effect.IO
import cats.implicits._

class InMemoryRepoSpec extends FlatSpec with Matchers {

  "The repository" should "get none if empty" in {
    val task = for {
      repo <- inMemoryRepo[IO]()
      getAll <- repo.getAll()
      get <- repo.getById(TodoID(0L))
    } yield getAll -> get

    val (getAll, get) = task.unsafeRunSync()

    getAll shouldBe empty
    get shouldBe None
  }

  it should "get an item" in {
    val task = for {
      repo <- inMemoryRepo[IO]()
      newItem <- repo.create(CreateTodoItem("Foo"))
      readBack <- repo.getById(newItem.id)
    } yield newItem -> readBack

    val (newItem, readBack) = task.unsafeRunSync()

    readBack shouldBe Some(newItem)
  }

  it should "get all items" in {
    val titles = List("foo", "bar")

    val task = for {
      repo <- inMemoryRepo[IO]()
      _ <- titles.traverse(title => repo.create(CreateTodoItem(title)))
      all <- repo.getAll()
    } yield all

    val allItems = task.unsafeRunSync()

    allItems.map(_.item.title) shouldBe titles
  }

  it should "delete an item" in {
    val task = for {
      repo <- inMemoryRepo[IO]()
      newItem <- repo.create(CreateTodoItem("Foo"))
      _ <- repo.delete(newItem.id)
      readBack <- repo.getById(newItem.id)
    } yield readBack

    val readBack = task.unsafeRunSync()

    readBack shouldBe None
  }

  it should "delete all items" in {
    val task = for {
      repo <- inMemoryRepo[IO]()
      _ <- repo.create(CreateTodoItem("Foo"))
      _ <- repo.deleteAll()
      getAll <- repo.getAll()
    } yield getAll

    val getAll = task.unsafeRunSync()

    getAll shouldBe empty
  }

  it should "update an item" in {
    val task = for {
      repo <- inMemoryRepo[IO]()
      item <- repo.create(CreateTodoItem("foo"))
      updated <- repo.update(item.id, UpdateTodoItem(Some("bar"), Some(true), Some(1)))
      readBack <- repo.getById(item.id)
    } yield readBack

    val item = task.unsafeRunSync()

    item shouldBe Some(TodoItem(TodoID(1), TodoBody("bar", true, 1)))
  }
}