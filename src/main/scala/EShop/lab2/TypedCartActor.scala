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
  case class AddItem(item: Any)                              extends Command
  case class RemoveItem(item: Any)                           extends Command
  case object ExpireCart                                     extends Command
  case class StartCheckout(orderManagerRef: ActorRef[Event]) extends Command
  case object ConfirmCheckoutCancelled                       extends Command
  case object ConfirmCheckoutClosed                          extends Command
  case class GetItems(sender: ActorRef[Cart])                extends Command // command made to make testing easier

  sealed trait State
  case class Empty(timers: TimerScheduler[TypedCartActor.Command])                  extends State
  case class NonEmpty(cart: Cart, timers: TimerScheduler[TypedCartActor.Command])   extends State
  case class InCheckout(cart: Cart, timers: TimerScheduler[TypedCartActor.Command]) extends State

  sealed trait Event
  case class ItemAdded(item: Any)                                          extends Event
  case class ItemRemoved(item: Any)                                        extends Event
  case object CartEmptied                                                  extends Event
  case object CheckoutStarted                                              extends Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
  case object CartExpired                                                  extends Event
  case object CheckoutClosed                                               extends Event
  case object CheckoutCancelled                                            extends Event

  def apply(): Behavior[Command] = Behaviors.setup(context => new TypedCartActor().start)
}

class TypedCartActor {
  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  var checkoutMapper: ActorRef[TypedCheckout.Event] = null

  private def scheduleTimer(timers: TimerScheduler[TypedCartActor.Command]) =
    timers.startSingleTimer(ExpireCart, ExpireCart, cartTimerDuration)

  def start: Behavior[TypedCartActor.Command] = Behaviors.setup { context =>
    checkoutMapper = context.messageAdapter(
      event =>
        event match {
          case TypedCheckout.CheckoutClosed    => ConfirmCheckoutClosed
          case TypedCheckout.CheckoutCancelled => ConfirmCheckoutCancelled
      }
    )

    Behaviors.withTimers(timers => empty(timers))
  }

  def empty(timers: TimerScheduler[TypedCartActor.Command]): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case AddItem(item) =>
          val cart = Cart.empty.addItem(item)
          scheduleTimer(timers)
          nonEmpty(cart, timers)

        case GetItems(sender) =>
          sender ! Cart.empty
          Behaviors.same
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

          case GetItems(sender) =>
            sender ! cart
            Behaviors.same

          case StartCheckout(orderManagerRef) =>
            timers.cancel(ExpireCart)

            val checkoutRef = context.spawn(TypedCheckout(checkoutMapper), "checkout")
            checkoutRef ! TypedCheckout.StartCheckout

            orderManagerRef ! CheckoutStarted(checkoutRef)
            inCheckout(cart, timers)

          case ExpireCart =>
            empty(timers)
      }
    )

  def inCheckout(
    cart: Cart,
    timers: TimerScheduler[TypedCartActor.Command]
  ): Behavior[TypedCartActor.Command] =
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
