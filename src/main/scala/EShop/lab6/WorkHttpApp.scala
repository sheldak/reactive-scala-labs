package EShop.lab6

import EShop.lab5.{ProductCatalog, SearchService}
import EShop.lab5.ProductCatalogHttpServer.{GetItems, Listing, Response}

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import java.net.URI
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.Try

/**
 * Basically a [[Worker]] that responds to the sender and does not stop
 */
// object HttpWorker {
//   sealed trait Command

//   case class Work(work: String, replyTo: ActorRef[WorkerResponse]) extends Command

//   case class WorkerResponse(work: String)

//   def apply(): Behavior[Command] =
//     Behaviors.receive(
//       (context, msg) =>
//         msg match {
//           case Work(work, replyTo) =>
//             context.log.info(s"I got to work on $work")
//             replyTo ! WorkerResponse("Done")
//             Behaviors.same
//       }
//     )
// }

// trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
//   case class WorkDTO(work: String)

//   implicit val workerDtoWork  = jsonFormat1(WorkDTO)
//   implicit val workerResponse = jsonFormat1(HttpWorker.WorkerResponse)

//   //custom formatter just for example
//   implicit val uriFormat = new JsonFormat[java.net.URI] {
//     override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)

//     override def read(json: JsValue): URI =
//       json match {
//         case JsString(url) => new URI(url)
//         case _             => throw new RuntimeException("Parsing exception")
//       }
//   }

// }

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)
    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }

  implicit val getItemsFormat = jsonFormat2(GetItems)
  implicit val itemFormat     = jsonFormat5(ProductCatalog.Item)
  implicit val responseFormat = jsonFormat1(Response)
}

object WorkHttpApp extends App {
  val workHttpServer = new WorkHttpServer()
  workHttpServer.run(Try(args(0).toInt).getOrElse(9000))
}

/**
 * The server that distributes all of the requests to the local workers spawned via router pool.
 */
class WorkHttpServer extends JsonSupport {

  implicit val system           = ActorSystem(Behaviors.empty, "ReactiveRouters")
  implicit val scheduler        = system.scheduler
  implicit val executionContext = system.executionContext
  val workers                   = system.systemActorOf(Routers.pool(3)(ProductCatalog(new SearchService())), "workersRouter")

  implicit val timeout: Timeout = 5.seconds

  def routes: Route =
    path("work") {
      post {
        entity(as[GetItems]) { request =>
          val response =
            workers.ask(ref => ProductCatalog.GetItems(request.brand, request.productKeyWords, ref))

          onSuccess(response) {
            case ProductCatalog.Items(items) => complete(items)
            case _                           => complete(StatusCodes.InternalServerError)
          }
        }
      }
    }

  def run(port: Int): Unit = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    println(s"Server now online. Please navigate to http://localhost:8080/hello\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
  }
}
