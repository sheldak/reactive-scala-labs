package EShop.lab5

import EShop.lab5.ProductCatalog
import EShop.lab5.ProductCatalogHttpServer.{GetItems, Listing}

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import java.net.URI
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}

import com.typesafe.config.ConfigFactory

object ProductCatalogHttpServer {
  sealed trait Command
  case class Listing(listing: Receptionist.Listing) extends Command

  case class GetItems(brand: String, productKeyWords: List[String])
  case class Response(items: List[ProductCatalog.Item])
}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)
    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }

  implicit val getItemsFormat = jsonFormat2(ProductCatalogHttpServer.GetItems)
  implicit val itemFormat     = jsonFormat5(ProductCatalog.Item)
  implicit val responseFormat = jsonFormat1(ProductCatalogHttpServer.Response)
}

object ProductCatalogHttpServerApp extends App {
  new ProductCatalogHttpServer().start(9000)
}

class ProductCatalogHttpServer extends JsonSupport {
  implicit val system = ActorSystem[Nothing](Behaviors.empty, "ProductCatalogHttp")

  implicit val timeout: Timeout = 3.seconds

  def routes(productCatalogRef: ActorRef[ProductCatalog.Query]): Route = {
    path("search") {
      get {
        entity(as[GetItems]) { request =>
          val response =
            productCatalogRef.ask(ref => ProductCatalog.GetItems(request.brand, request.productKeyWords, ref))

          onSuccess(response) {
            case ProductCatalog.Items(items) => complete(items)
            case _                           => complete(StatusCodes.InternalServerError)
          }
        }
      }
    }
  }

  def start(port: Int) = {
    Behaviors.setup[ProductCatalogHttpServer.Command] { context =>
      val listingAdapter = context.messageAdapter[Receptionist.Listing](Listing)

      system.receptionist ! Receptionist.Find(ProductCatalog.ProductCatalogServiceKey, listingAdapter)

      Behaviors.receiveMessage {
        case Listing(ProductCatalog.ProductCatalogServiceKey.Listing(listing)) =>
          val bindingFuture = Http().newServerAt("localhost", port).bind(routes(listing.head))

          Behaviors.ignore
      }
    }

    Await.ready(system.whenTerminated, Duration.Inf)
  }
}
