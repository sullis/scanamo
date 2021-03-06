package org.scanamo.ops

import cats.~>
import cats.syntax.either._
import com.amazonaws.services.dynamodbv2.model._
import org.scanamo.ops.retrypolicy._

import akka.stream.alpakka.dynamodb.{ AwsOp, AwsPagedOp, DynamoAttributes, DynamoClient }
import akka.stream.alpakka.dynamodb.scaladsl.DynamoDb
import akka.stream.scaladsl.Source
import akka.NotUsed

private[scanamo] class AlpakkaInterpreter(client: DynamoClient, retryPolicy: RetryPolicy)
    extends (ScanamoOpsA ~> AlpakkaInterpreter.Alpakka)
    with WithRetry {

  final private def run(op: AwsOp): AlpakkaInterpreter.Alpakka[op.B] =
    retry(DynamoDb.source(op).withAttributes(DynamoAttributes.client(client)), retryPolicy)

  def apply[A](ops: ScanamoOpsA[A]) =
    ops match {
      case Put(req)        => run(JavaRequests.put(req))
      case Get(req)        => run(req)
      case Delete(req)     => run(JavaRequests.delete(req))
      case Scan(req)       => run(AwsPagedOp.create(JavaRequests.scan(req)))
      case Query(req)      => run(AwsPagedOp.create(JavaRequests.query(req)))
      case Update(req)     => run(JavaRequests.update(req))
      case BatchWrite(req) => run(req)
      case BatchGet(req)   => run(req)
      case ConditionalDelete(req) =>
        run(JavaRequests.delete(req))
          .map(Either.right[ConditionalCheckFailedException, DeleteItemResult])
          .recover {
            case e: ConditionalCheckFailedException => Either.left(e)
          }
      case ConditionalPut(req) =>
        run(JavaRequests.put(req))
          .map(Either.right[ConditionalCheckFailedException, PutItemResult])
          .recover {
            case e: ConditionalCheckFailedException => Either.left(e)
          }
      case ConditionalUpdate(req) =>
        run(JavaRequests.update(req))
          .map(Either.right[ConditionalCheckFailedException, UpdateItemResult])
          .recover {
            case e: ConditionalCheckFailedException => Either.left(e)
          }
    }
}

object AlpakkaInterpreter {
  type Alpakka[A] = Source[A, NotUsed]
}
