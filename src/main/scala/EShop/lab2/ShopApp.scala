package EShop.lab2

import akka.actor._
import scala.concurrent.Await
import scala.concurrent.duration._

import EShop.lab2.{CartActor, Checkout}

object ShopApp extends App {
  val system = ActorSystem("Shop")

  val cart = system.actorOf(Props[CartActor], "cart")
  cart ! CartActor.AddItem("item")

  val checkout = system.actorOf(Props[Checkout], "checkout")
  checkout ! Checkout.StartCheckout

  Await.result(system.whenTerminated, Duration.Inf)
}
