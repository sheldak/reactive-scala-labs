package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.OrderManager

import scala.concurrent._
import ExecutionContext.Implicits.global
import java.awt.Component.BaselineResizeBehavior

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                                             extends Command
  case class RemoveItem(item: Any)                                          extends Command
  case object ExpireCart                                                    extends Command
  case class StartCheckout(orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ConfirmCheckoutCancelled                                      extends Command
  case object ConfirmCheckoutClosed                                         extends Command
  case class GetItems(sender: ActorRef[Cart])                               extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
}

class TypedCartActor {
  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(timers: TimerScheduler[TypedCartActor.Command]) =
    timers.startSingleTimer(ExpireCart, ExpireCart, cartTimerDuration)

  def start: Behavior[TypedCartActor.Command] = Behaviors.withTimers(timers => empty(timers))

  def empty(timers: TimerScheduler[TypedCartActor.Command]): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case AddItem(item) =>
          val cart = Cart.empty.addItem(item)
          scheduleTimer(timers)
          nonEmpty(cart, timers)
    }
  )

  def nonEmpty(cart: Cart, timers: TimerScheduler[TypedCartActor.Command]): Behavior[TypedCartActor.Command] =
    Behaviors.receive(
      (context, msg) =>
        msg match {
          case AddItem(item) =>
            val updatedCart = cart.addItem(item)
            nonEmpty(updatedCart, timers)

          case RemoveItem(item) =>
            if (cart.contains(item)) {
              val updatedCart = cart.removeItem(item)

              if (updatedCart.size == 0) {
                timers.cancel(ExpireCart)
                empty(timers)
              } else {
                scheduleTimer(timers)
                nonEmpty(updatedCart, timers)
              }
            } else
              Behaviors.same

          case ExpireCart =>
            empty(timers)

          case StartCheckout =>
            inCheckout(cart, timers)
      }
    )

  def inCheckout(cart: Cart, timers: TimerScheduler[TypedCartActor.Command]): Behavior[TypedCartActor.Command] =
    Behaviors.receive(
      (context, msg) =>
        msg match {
          case ConfirmCheckoutClosed =>
            empty(timers)

          case ConfirmCheckoutCancelled =>
            scheduleTimer(timers)
            nonEmpty(cart, timers)
      }
    )
}
