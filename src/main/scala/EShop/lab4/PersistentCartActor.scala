package EShop.lab4

import EShop.lab2.{Cart, TypedCartActor, TypedCheckout}
import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class PersistentCartActor {
  import EShop.lab2.TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5.seconds

  var checkoutMapper: ActorRef[TypedCheckout.Event] = null

  private def scheduleTimer(timers: TimerScheduler[TypedCartActor.Command]) =
    timers.startSingleTimer(ExpireCart, ExpireCart, cartTimerDuration)

  def apply(persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { context =>
    checkoutMapper = context.messageAdapter(
      event =>
        event match {
          case TypedCheckout.CheckoutClosed    => ConfirmCheckoutClosed
          case TypedCheckout.CheckoutCancelled => ConfirmCheckoutCancelled
      }
    )

    Behaviors.withTimers(
      timers =>
        EventSourcedBehavior[Command, Event, State](
          persistenceId,
          Empty(timers),
          commandHandler(context),
          eventHandler(context)
      )
    )
  }

  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case Empty(timers) =>
        command match {
          case AddItem(item) =>
            Effect.persist(ItemAdded(item))

          case _ =>
            Effect.none
        }

      case NonEmpty(cart, timers) =>
        command match {
          case AddItem(item) =>
            Effect.persist(ItemAdded(item))

          case RemoveItem(item) =>
            if (cart.contains(item)) {
              if (cart.size == 1) {
                Effect.persist(CartEmptied)
              } else {
                Effect.persist(ItemRemoved(item))
              }
            } else
              Effect.none

          case StartCheckout(orderManagerRef) =>
            val checkoutRef = context.spawn(TypedCheckout(checkoutMapper), "checkout")
            checkoutRef ! TypedCheckout.StartCheckout

            orderManagerRef ! CheckoutStarted(checkoutRef)

            Effect.persist(CheckoutStarted)

          case ExpireCart =>
            Effect.persist(CartExpired)

          case _ =>
            Effect.none
        }

      case InCheckout(_, timers) =>
        command match {
          case ConfirmCheckoutClosed =>
            Effect.persist(CheckoutClosed)

          case ConfirmCheckoutCancelled =>
            Effect.persist(CheckoutCancelled)

          case _ =>
            Effect.none
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    state match {
      case Empty(timers) =>
        event match {
          case ItemAdded(item) =>
            scheduleTimer(timers)
            NonEmpty(Cart.empty.addItem(item), timers)
        }

      case NonEmpty(cart, timers) =>
        event match {
          case ItemAdded(item) =>
            scheduleTimer(timers)
            NonEmpty(cart.addItem(item), timers)

          case ItemRemoved(item) =>
            scheduleTimer(timers)
            NonEmpty(cart.removeItem(item), timers)

          case CartEmptied =>
            timers.cancel(ExpireCart)
            Empty(timers)

          case CheckoutStarted =>
            timers.cancel(ExpireCart)
            InCheckout(cart, timers)

          case CartExpired =>
            Empty(timers)
        }

      case InCheckout(cart, timers) =>
        event match {
          case CheckoutClosed =>
            Empty(timers)

          case CheckoutCancelled =>
            scheduleTimer(timers)
            NonEmpty(cart, timers)
        }
    }
  }

}
