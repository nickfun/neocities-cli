package gs.nick

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse}
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.stream.Materializer
import cats.data.EitherT
import cats.implicits._
import generated.client.Client
import generated.client.definitions.FileEntry
import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

sealed trait NeocitiesError

case class ApiError(
    msg: String,
    resp: HttpResponse)
    extends NeocitiesError
case class TodoError(msg: String) extends NeocitiesError

case class NeocitiesLayer(neoClient: Client)(implicit ec: ExecutionContext) {

  def allRemoteFiles(): EitherT[Future, NeocitiesError, List[FileEntry]] = {
    neoClient
      .listFiles()
      .leftMap { err =>
        val message = "Error when getting the list of files from NeoCities API"
        val result: NeocitiesError = err match {
          case Left(_: Throwable) => TodoError(message)
          case Right(value: HttpResponse) => ApiError(message, value)
        }
        result
      }
      .map { resp =>
        resp.fold(serverData => serverData.files.toList)
      }

  }
}

object Neocities {

  val threadPool: ExecutorService = Executors.newCachedThreadPool()
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: Materializer = Materializer.matFromSystem

  implicit val ec: ExecutionContext = {
    ExecutionContext.fromExecutorService(threadPool)
  }

  def buildClient(): Client = {
    def loggingClient(x: HttpRequest): Future[HttpResponse] = {
      println(s"REQUEST: ${x.method.value} ${x.uri}")
      Http().singleRequest(x).map { result =>
        println(s"RESPONSE: ${result.status}: ${result.entity}")
        result
      }
    }
    Client()(loggingClient, ec, mat)
  }

  val header: List[HttpHeader] = {
    val user = sys.env.getOrElse("NEOCITIES_USER", throw new Exception("missing env USERNAME"))
    val pass = sys.env.getOrElse("NEOCITIES_PASS", throw new Exception("missing env PASSWORD"))
    println(s"user=$user pass=$pass")
    List(Authorization(BasicHttpCredentials(user, pass)))
  }

  def main(args: Array[String]): Unit = {
    val client = buildClient()
    val result = client
      .listFiles(header)
      .fold(
        err => {
          err match {
            case Left(e) =>
              println(s"ERROR: Got an exception ${e}")
              "error - exception"
            case Right(response) =>
              println(s"ERROR: Got a response $response")
              response.discardEntityBytes()
              "error - http response"
          }
        },
        ok200 => {
          println(s"SUCCESS")
          ok200.fold(ok => {
            val snip = ok.files.take(5).map(_.toString).mkString("\n")
            println(snip)
          })
          "success!"
        }
      )

    Await.result(result, Duration.Inf)
    println("All Done")
    system.terminate()
    threadPool.shutdown()
  }
}
