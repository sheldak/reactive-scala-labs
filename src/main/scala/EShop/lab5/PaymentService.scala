package EShop.lab5

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object PaymentService {

  sealed trait Response
  case object PaymentSucceeded extends Response

  case class PaymentClientError() extends Exception
  case class PaymentServerError() extends Exception

  def apply(
    method: String,
    payment: ActorRef[Response]
  ): Behavior[HttpResponse] = Behaviors.setup { context =>
    implicit val system           = context.system
    implicit val executionContext = context.system.executionContext

    val response: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = getURI(method)))

    response
      .onComplete {
        case Success(res) => context.self ! res
        case Failure(ex)  => throw ex
      }

    Behaviors.receiveMessage {
      case HttpResponse(s: StatusCodes.Success, _, _, _) =>
        payment ! PaymentSucceeded
        Behaviors.stopped

      case HttpResponse(s: StatusCodes.ClientError, _, _, _) =>
        throw PaymentClientError()

      case HttpResponse(s: StatusCodes.ServerError, _, _, _) =>
        throw PaymentServerError()
    }
  }

  // remember running PymentServiceServer() before trying payu based payments
  private def getURI(method: String) = method match {
    case "payu"   => "http://127.0.0.1:8082"
    case "paypal" => s"http://httpbin.org/status/408"
    case "visa"   => s"http://httpbin.org/status/200"
    case _        => s"http://httpbin.org/status/404"
  }
}
