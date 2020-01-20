package bertuol.todobackend

import java.{util => ju}
import java.util.{concurrent => juc}

import cats.Applicative
import cats.Monad
import cats.effect.Async
import cats.effect.Sync
import cats.effect.concurrent.Ref
import software.amazon.awssdk.services.dynamodb.model._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.auth.credentials._
import software.amazon.awssdk.regions.Region
import io.chrisdavenport.log4cats.Logger

import scala.jdk.CollectionConverters._

object repository {
  import domain._
  import cats.syntax.traverse._
  import cats.instances.list._
  import cats.syntax.functor._
  import cats.syntax.flatMap._
  import cats.syntax.applicativeError._
  import cats.syntax.apply._

  trait TodoRepository[F[_]] {

    def getAll(): F[List[TodoItem]]

    def getById(id: TodoID): F[Option[TodoItem]]

    def delete(id: TodoID): F[Unit]

    def deleteAll(): F[Unit]

    def create(action: CreateTodoItem): F[TodoItem]

    def update(id: TodoID, action: UpdateTodoItem): F[Option[TodoItem]]
  }

  object TodoRepository {
    def apply[F[_]: TodoRepository]: TodoRepository[F] = implicitly
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  def bootstrapRepo[F[_]: Async: Logger](): F[TodoRepository[F]] = {
    implicit val settings = defaultSettings[F]()
    for {
      ddbClient <- Async[F].delay {
        val credentials: AwsCredentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create("foo", "bar"))
        DynamoDbAsyncClient
          .builder()
          .endpointOverride(new java.net.URI("http://localhost:8000"))
          .credentialsProvider(credentials)
          .region(Region.EU_WEST_1)
          .build()
      }
      repo        <- Async[F].delay(new DynamoDbRepo[F](ddbClient))
      _           <- Logger[F].info("Built client")
      tableExists <- repo.tableExists
      _           <- Logger[F].info(s"table exists? $tableExists")
      _           <- if (tableExists) Async[F].unit else repo.createTable
      _           <- Logger[F].info("done bootstraping")
    } yield repo
  }

  trait DynamoDbSettings[F[_]] {
    def tableName: F[String]
    def partitionKey: F[String]
  }

  object DynamoDbSettings {
    def apply[F[_]: DynamoDbSettings]: DynamoDbSettings[F] = implicitly
  }

  def defaultSettings[F[_]: Applicative](): DynamoDbSettings[F] = new DynamoDbSettings[F] {
    override def tableName: F[String]    = Applicative[F].pure("todos")
    override def partitionKey: F[String] = Applicative[F].pure("id")
  }

  final class DynamoDbRepo[F[_]: Async: DynamoDbSettings: Logger](ddbClient: DynamoDbAsyncClient) extends TodoRepository[F] {
    import DynamoDbProtocol._

    private val settings: DynamoDbSettings[F] = implicitly

    def tableExists: F[Boolean] = {
      for {
        tableName <- settings.tableName
        request = DescribeTableRequest.builder.tableName(tableName).build
        attemptResponse <- doRequest(request, ddbClient.describeTable(_: DescribeTableRequest)).attempt
        result <- attemptResponse match {
          case Left(_: ResourceNotFoundException) => Async[F].pure(false)
          case Left(ex)                           => Logger[F].error(ex)("error") *> Async[F].raiseError(ex)
          case Right(_)                           => Async[F].pure(true)
        }
      } yield result
    }

    def createTable: F[Unit] = {
      for {
        tableName    <- settings.tableName
        partitionKey <- settings.partitionKey
        request = CreateTableRequest.builder
          .tableName(tableName)
          .keySchema(KeySchemaElement.builder.attributeName(partitionKey).keyType(KeyType.HASH).build)
          .attributeDefinitions(AttributeDefinition.builder.attributeName(partitionKey).attributeType(ScalarAttributeType.S).build)
          .billingMode(BillingMode.PAY_PER_REQUEST)
          .build
        _ <- doRequest(request, ddbClient.createTable(_: CreateTableRequest))
      } yield ()
    }

    override def create(action: CreateTodoItem): F[TodoItem] = {
      for {
        newId <- TodoID.random
        todo = action.todoItem(newId)
        request <- toCreateTodoRequest(todo)
        _       <- doRequest(request, ddbClient.putItem(_: PutItemRequest))
      } yield todo
    }

    override def delete(id: TodoID): F[Unit] = {
      for {
        request <- toDeleteTodoRequest(id)
        _       <- doRequest(request, ddbClient.deleteItem(_: DeleteItemRequest))
      } yield ()
    }

    override def deleteAll(): F[Unit] = {
      for {
        allTodos <- getAll()
        allIds = allTodos.map(_.id)
        requests <- toDeleteTodoRequest(allIds)
        _        <- requests.traverse(doRequest(_, ddbClient.batchWriteItem(_: BatchWriteItemRequest))).as(())
      } yield ()
    }

    override def getAll(): F[List[TodoItem]] = {
      for {
        request  <- toGetAllRequest()
        response <- doRequest(request, ddbClient.scan(_: ScanRequest))
        items    <- response.items.asScala.toList.traverse(toTodoItem(_))
      } yield items
    }

    override def getById(id: TodoID): F[Option[TodoItem]] = {
      for {
        request  <- toGetTodoRequest(id)
        response <- doRequest(request, ddbClient.getItem(_: GetItemRequest))
        item <- response.item match {
          case attrs if attrs.isEmpty() => Async[F].pure(None)
          case attrs                    => toTodoItem(attrs).map(Some(_))
        }
      } yield item
    }

    override def update(id: TodoID, action: UpdateTodoItem): F[Option[TodoItem]] = {
      for {
        request  <- toUpdateTodoRequest(id, action)
        response <- doRequest(request, ddbClient.updateItem(_: UpdateItemRequest)).attempt
        item <- response match {
          case Left(_: ConditionalCheckFailedException) => Async[F].pure(None)
          case Left(ex)                                 => Logger[F].error(ex)(s"error updating $id") *> Async[F].raiseError(ex)
          case Right(r) if r.attributes.isEmpty         => Async[F].pure(None)
          case Right(r)                                 => toTodoItem(r.attributes).map(Some(_))
        }
      } yield item
    }
  }

  object DynamoDbProtocol {

    def toUpdateTodoRequest[F[_]: Async: DynamoDbSettings](id: TodoID, action: UpdateTodoItem): F[UpdateItemRequest] = {
      for {
        tableName    <- DynamoDbSettings[F].tableName
        partitionKey <- DynamoDbSettings[F].partitionKey
        ddbKey              = Map(partitionKey -> AttributeValue.builder.s(id.value).build)
        conditionExpression = "attribute_exists(#partitionKey)"
        expr = List(
          action.title.map(title => ("#title = :title", Map("#title"             -> "title"), Map(":title"         -> AttributeValue.builder.s(title).build))),
          action.order.map(order => ("#order = :order", Map("#order"             -> "order"), Map(":order"         -> AttributeValue.builder.n(order.toString).build))),
          action.completed.map(c => ("#completed = :completed", Map("#completed" -> "completed"), Map(":completed" -> AttributeValue.builder.bool(c).build)))
        ).flatten.fold(("SET ", Map("#partitionKey" -> partitionKey), Map.empty[String, AttributeValue]))((acc, v) => (acc._1 + v._1, acc._2 ++ v._2, acc._3 ++ v._3))

        request = UpdateItemRequest.builder
          .tableName(tableName)
          .key(ddbKey.asJava)
          .conditionExpression(conditionExpression)
          .updateExpression(expr._1)
          .expressionAttributeNames(expr._2.asJava)
          .expressionAttributeValues(expr._3.asJava)
          .returnValues(ReturnValue.ALL_NEW)
          .build
      } yield request
    }

    def toCreateTodoRequest[F[_]: Async: DynamoDbSettings](todo: TodoItem): F[PutItemRequest] = {
      for {
        tableName    <- DynamoDbSettings[F].tableName
        partitionKey <- DynamoDbSettings[F].partitionKey
        item = Map(
          partitionKey -> AttributeValue.builder.s(todo.id.value).build,
          "title"      -> AttributeValue.builder.s(todo.item.title).build,
          "completed"  -> AttributeValue.builder.bool(todo.item.completed).build,
          "order"      -> AttributeValue.builder.n(todo.item.order.toString).build
        )
        conditionExpression      = "attribute_not_exists(#partitionKey)"
        expressionAttributeNames = Map("#partitionKey" -> partitionKey)

        request = PutItemRequest.builder
          .tableName(tableName)
          .item(item.asJava)
          .conditionExpression(conditionExpression)
          .expressionAttributeNames(expressionAttributeNames.asJava)
          .build
      } yield request
    }

    def toDeleteTodoRequest[F[_]: Async: DynamoDbSettings](id: TodoID): F[DeleteItemRequest] = {
      for {
        tableName    <- DynamoDbSettings[F].tableName
        partitionKey <- DynamoDbSettings[F].partitionKey
        ddbKey = Map(partitionKey -> AttributeValue.builder.s(id.value).build)
        request = DeleteItemRequest.builder
          .tableName(tableName)
          .key(ddbKey.asJava)
          .build
      } yield request
    }

    def toDeleteTodoRequest[F[_]: Async: DynamoDbSettings](ids: List[TodoID]): F[List[BatchWriteItemRequest]] = {
      for {
        tableName    <- DynamoDbSettings[F].tableName
        partitionKey <- DynamoDbSettings[F].partitionKey
        requests = ids.grouped(25).toList.map { batch =>
          val items = batch.map { id =>
            WriteRequest.builder
              .deleteRequest(
                DeleteRequest.builder
                  .key(Map(partitionKey -> AttributeValue.builder.s(id.value).build).asJava)
                  .build
              )
              .build
          }
          val requestItems = Map(tableName -> items.asJava)
          BatchWriteItemRequest.builder
            .requestItems(requestItems.asJava)
            .build
        }
      } yield requests
    }

    def toTodoItem[F[_]: Async: DynamoDbSettings](attributes: ju.Map[String, AttributeValue]): F[TodoItem] =
      for {
        partitionKey <- DynamoDbSettings[F].partitionKey
        _id          <- Async[F].catchNonFatal(attributes.get(partitionKey).s()).map(TodoID(_))
        title        <- Async[F].catchNonFatal(attributes.get("title").s)
        completed    <- Async[F].catchNonFatal(attributes.get("completed").bool)
        order        <- Async[F].catchNonFatal(attributes.get("order").n.toInt)
        body = TodoBody(title, completed, order)
      } yield TodoItem(_id, body)

    def toGetAllRequest[F[_]: Async: DynamoDbSettings](): F[ScanRequest] = {
      for {
        tableName <- DynamoDbSettings[F].tableName
        request = ScanRequest.builder
          .tableName(tableName)
          .build
      } yield request
    }

    def toGetTodoRequest[F[_]: Async: DynamoDbSettings](id: TodoID): F[GetItemRequest] = {
      for {
        tableName    <- DynamoDbSettings[F].tableName
        partitionKey <- DynamoDbSettings[F].partitionKey
        ddbKey = Map(partitionKey -> AttributeValue.builder.s(id.value).build)
        request = GetItemRequest.builder
          .tableName(tableName)
          .key(ddbKey.asJava)
          .build
      } yield request
    }

    def doRequest[F[_]: Async, Request <: DynamoDbRequest, Response](request: Request, call: (Request) => juc.CompletableFuture[Response]): F[Response] =
      Async[F].async[Response] { cb =>
        call(request).whenComplete { (r, error) =>
          if (error == null) cb(Right(r))
          else cb(Left(error.getCause))
        }
        ()
      }

  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  def inMemoryRepo[F[_]: Sync](): F[TodoRepository[F]] =
    for {
      db <- Ref.of(Map[TodoID, TodoItem]())
      repo = new InMemoryRepo(db)
    } yield repo

  private class InMemoryRepo[F[_]: Sync](db: Ref[F, Map[TodoID, TodoItem]]) extends TodoRepository[F] {

    override def getAll(): F[List[TodoItem]] = db.get.map(_.values.toList)

    override def getById(id: TodoID): F[Option[TodoItem]] = db.get.map(_.get(id))

    override def delete(id: TodoID): F[Unit] = db.update(_ - id)

    override def deleteAll(): F[Unit] = db.update(_.empty)

    override def create(action: CreateTodoItem): F[TodoItem] =
      for {
        newId <- TodoID.random
        newTodoItem = action.todoItem(newId)
        _ <- db.update(_ + (newId -> newTodoItem))
      } yield newTodoItem

    override def update(id: TodoID, action: UpdateTodoItem): F[Option[TodoItem]] =
      for {
        oldValue <- getById(id)
        result <- oldValue.fold(Monad[F].pure[Option[TodoItem]](None)) { x =>
          val newValue = action.todoItem(x)
          db.update(_ + (id -> newValue)) *> Monad[F].pure(Some(newValue))
        }
      } yield result
  }
}
