package EShop.lab2

import akka.actor.{Actor, ActorRef, Props, Timers}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor with Timers {
  import CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5 seconds

  private def scheduleTimer: Unit =
    timers.startSingleTimer(ExpireCart, ExpireCart, cartTimerDuration)

  override def preStart: Unit = context become empty

  def receive: Receive = LoggingReceive {
    case _ => {}
  }

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      val cart = Cart.empty.addItem(item)
      scheduleTimer
      context become nonEmpty(cart)
  }

  def nonEmpty(cart: Cart): Receive = LoggingReceive {
    case AddItem(item) =>
      val updatedCart = cart.addItem(item)
      scheduleTimer
      context become nonEmpty(updatedCart)

    case RemoveItem(item) =>
      if (cart.contains(item)) {
        val updatedCart = cart.removeItem(item)

        if (updatedCart.size == 0) {
          timers.cancel(ExpireCart)
          context become empty
        } else {
          scheduleTimer
          context become nonEmpty(updatedCart)
        }
      }

    case ExpireCart =>
      context become empty

    case StartCheckout =>
      context become inCheckout(cart)
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutClosed =>
      context become empty

    case ConfirmCheckoutCancelled =>
      context become nonEmpty(cart)
  }
}
