package gs.nick

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse}
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.stream.Materializer
import cats.implicits._
import cats.data._
import generated.client.{Client, ListFilesResponse}

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

object Neocities {

  val pool: ExecutorService = Executors.newCachedThreadPool()

  implicit val ec: ExecutionContext = {
    ExecutionContext.fromExecutorService(pool)
  }

  def buildClient(): Client = {
    implicit val system: ActorSystem = ActorSystem()
    val mat: Materializer = Materializer.matFromSystem
    implicit def myLoggingClient(x: HttpRequest): Future[HttpResponse] = {
      println(s"REQUEST: ${x.method.value} ${x.uri}")
      Http().singleRequest(x).map { result =>
        println(s"RESPONSE: ${result.status}: ${result.entity}")
        result
      }
    }
    Client()(myLoggingClient, ec, mat)
  }

  val header: List[HttpHeader] = {
    val user = sys.env.getOrElse("NEOCITIES_USER", throw new Exception("missing env USERNAME"))
    val pass = sys.env.getOrElse("NEOCITIES_PASS", throw new Exception("missing env PASSWORD"))
    println(s"user=$user pass=$pass")
    List(Authorization(BasicHttpCredentials(user, pass)))
  }

  def main(args: Array[String]): Unit = {
    println("BEGIN")
    //demo()
    println("NOW HTTP CALL")
    val client = buildClient()
    val result = client
      .listFiles(header)
      .map { result =>
        result match {
          case ListFilesResponse.OK(value) => println(s"RESULT $value")
        }
        pool.shutdown()
        pool.shutdownNow()
        println("pool shutdown donw")
        System.exit(0)
      }
      .leftMap { err =>
        err match {
          case Left(value) =>
            println(s"ERROR got throwable $value")
          case Right(value) =>
            println(s"ERROR got http response $value")
            value.discardEntityBytes()
        }
        pool.shutdown()
        pool.shutdownNow()
        println("pool is shut")
        System.exit(1)
      }
    Await.result(result.value, Duration.Inf)
    println("All Done")
    pool.shutdownNow()
    println("Pool is down")
  }
}
