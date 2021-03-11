package gs.nick

import java.io.File
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse}
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.stream.Materializer
import cats.data.EitherT
import cats.implicits._
import generated.clients.neocities.Client
import generated.clients.neocities.definitions.FileEntry

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

case class Sha1(hash: String)

sealed trait NeocitiesError
case class ApiError(msg: String, resp: HttpResponse) extends NeocitiesError
case class TodoError(msg: String) extends NeocitiesError

/**
 * Interact with Neocities API
 */
case class NeocitiesService(neoClient: Client, projectRoot: String)(implicit ec: ExecutionContext) {

  val authHeaders: List[HttpHeader] = {
    val user = sys.env.getOrElse("NEOCITIES_USER", throw new Exception("missing env USERNAME"))
    val pass = sys.env.getOrElse("NEOCITIES_PASS", throw new Exception("missing env PASSWORD"))
    println(s"[Neocities API Auth user=$user pass=$pass]")
    List(Authorization(BasicHttpCredentials(user, pass)))
  }

  def allRemoteFiles(): EitherT[Future, NeocitiesError, List[FileEntry]] = {
    neoClient
      .listFiles(authHeaders)
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

  def pathToSha1(entries: List[FileEntry]): Map[String, Sha1] = {
    entries
      .filter(_.sha1Hash.isDefined)
      .map { file =>
        (file.path, Sha1(file.sha1Hash.get))
      }
      .toMap
  }

  def validateGiven(entries: List[FileEntry]): Boolean = {
    var valid = true
    val header = Seq(Seq("Status", "File", "Local Sha1", "Remote Sha1"))
    val table: Seq[Seq[String]] = entries.toSeq.map { row =>
      val local = new File(projectRoot + row.path)
      val localSha1 = getSha1(local)
      if (localSha1 == row.sha1Hash.map(Sha1).get) {
        Seq("GOOD", local.getName, localSha1.hash, row.sha1Hash.get)
      } else {
        valid = false
        Seq("BAD", local.getName, localSha1.hash, row.sha1Hash.get)
      }
    }
    println(Tabulator.format(header ++ table))
    valid
  }

  def getSha1(file: File): Sha1 = {
    try {
      Sha1(HashGenerator.generate("SHA1", file.getAbsolutePath))
    } catch {
      case e: Throwable => {
        println(s"ERROR CALCULATING SHA1 FOR ${file.getAbsolutePath} $e")
        Sha1("xxx")
      }
    }
  }

  def compareSha(file: File, expected: Sha1): Boolean = {
    getSha1(file) == expected
  }
}

object Config {
  val CMD_PUSH = "push"
  val CMD_PULL = "pull"
  val CMD_REPORT_PUSH = "report-push"
  val CMD_REPORT_PULL = "report-pull"

  case class CliConfig(
      command: String,
      authFile: String,
      projectRoot: String) {

    def isValid: Boolean = {
      val validCmds = List(CMD_PUSH, CMD_PULL, CMD_REPORT_PULL, CMD_REPORT_PUSH)
      val authF = new File(authFile)
      val rootF = new File(projectRoot)
      validCmds.contains(command) && authF.exists() && rootF.isDirectory
    }
  }
}

case class LocalFile(file: File) {
  def sha1: Sha1 = Sha1(HashService.sha1(file))

  def areSame(other: FileEntry): Boolean = {
    other.sha1Hash.contains(sha1.hash)
  }

  def pathName(root: File): String = {
    file.getCanonicalPath.drop(1 + root.getCanonicalPath.length)
  }
}

case class LocalFileService(projectRoot: File)(implicit ec: ExecutionContext) {

  def allFiles: Map[String, LocalFile] = {
    allInFolder(projectRoot)
      .map(LocalFile)
      .map { e =>
        (e.pathName(projectRoot), e)
      }
      .toMap
  }

  def allInFolder(input: File): List[File] = {
    val here = input.listFiles.toList.filter(_.isFile)
    val deeper: List[File] = input.listFiles.toList.filter(_.isDirectory).flatMap(allInFolder)
    here ++ deeper
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

  def main(args: Array[String]): Unit = {
    val client = buildClient()
    val neocities = NeocitiesService(client, "/Users/nfunnell/junk/jeremy-parish-fanclub/_site/")
    val result = neocities
      .allRemoteFiles()
      .fold(
        err => {
          println("Error! " + err.toString)
          0
        },
        files => {
          println("Got files from the server!")
          val test = files.filterNot(_.isDirectory).slice(100, 110)
          neocities.validateGiven(test)
          files.filterNot(_.isDirectory).length
        }
      )
      .map { numFiles => println(s"Remote Files: $numFiles"); numFiles }

    Await.result(result, Duration.Inf)
    println("All Done")
    system.terminate()
    threadPool.shutdown()
  }
}
