package EShop.lab4

import EShop.lab2.TypedCartActor
import EShop.lab3.{OrderManager, Payment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class PersistentCheckout {

  import EShop.lab2.TypedCheckout._

  val timerDuration: FiniteDuration = 1.seconds

  var paymentMapper: ActorRef[Payment.Event] = null

  def apply(cartActor: ActorRef[Event], persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      paymentMapper = context.messageAdapter(
        event =>
          event match {
            case Payment.PaymentReceived => ConfirmPaymentReceived
        }
      )

      Behaviors.withTimers(
        timers =>
          EventSourcedBehavior(
            persistenceId,
            WaitingForStart(timers),
            commandHandler(context, cartActor),
            eventHandler(context)
        )
      )
    }

  def commandHandler(
    context: ActorContext[Command],
    cartActor: ActorRef[Event]
  ): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case WaitingForStart(timers) =>
        command match {
          case StartCheckout =>
            Effect.persist(CheckoutStarted)

          case _ =>
            Effect.none
        }

      case SelectingDelivery(timers) =>
        command match {
          case SelectDeliveryMethod(method) =>
            Effect.persist(DeliveryMethodSelected)

          case CancelCheckout =>
            Effect.persist(CheckoutCancelled)

          case ExpireCheckout =>
            Effect.persist(CheckoutCancelled)

          case _ =>
            Effect.none
        }

      case SelectingPaymentMethod(timers) =>
        command match {
          case SelectPayment(payment, managerCheckoutMapper, managerPaymentMapper) =>
            val paymentRef = context.spawn(Payment(payment, managerPaymentMapper, paymentMapper), "payment")
            managerCheckoutMapper ! PaymentStarted(paymentRef)

            Effect.persist(PaymentStarted)

          case CancelCheckout =>
            Effect.persist(CheckoutCancelled)

          case ExpireCheckout =>
            Effect.persist(CheckoutCancelled)

          case _ =>
            Effect.none
        }

      case ProcessingPayment(timers) =>
        command match {
          case ConfirmPaymentReceived =>
            cartActor ! CheckoutClosed
            Effect.persist(CheckoutClosed)

          case CancelCheckout =>
            timers.cancel(ExpirePayment)
            cartActor ! CheckoutCancelled
            Effect.persist(CheckoutCancelled)

          case ExpireCheckout =>
            Effect.persist(CheckoutCancelled)

          case ExpirePayment =>
            Effect.persist(CheckoutCancelled)

          case _ =>
            Effect.none
        }

      case Cancelled(timers) =>
        command match {
          case StartCheckout =>
            Effect.persist(CheckoutStarted)

          case _ =>
            Effect.none
        }

      case Closed(timers) =>
        command match {
          case StartCheckout =>
            Effect.persist(CheckoutStarted)

          case _ =>
            Effect.none
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    state match {
      case WaitingForStart(timers) =>
        event match {
          case CheckoutStarted =>
            timers.startSingleTimer(ExpireCheckout, ExpireCheckout, timerDuration)
            SelectingDelivery(timers)
        }

      case SelectingDelivery(timers) =>
        event match {
          case DeliveryMethodSelected => SelectingPaymentMethod(timers)
          case CheckoutCancelled =>
            timers.cancel(ExpireCheckout)
            Cancelled(timers)
        }

      case SelectingPaymentMethod(timers) =>
        event match {
          case PaymentStarted =>
            timers.cancel(ExpireCheckout)
            timers.startSingleTimer(ExpirePayment, ExpirePayment, timerDuration)
            ProcessingPayment(timers)
          case CheckoutCancelled =>
            timers.cancel(ExpireCheckout)
            Cancelled(timers)
        }

      case ProcessingPayment(timers) =>
        event match {
          case CheckoutClosed =>
            timers.cancel(ExpirePayment)
            Closed(timers)
          case CheckoutCancelled =>
            timers.cancel(ExpirePayment)
            Cancelled(timers)
        }

      case Closed(timers) =>
        event match {
          case CheckoutStarted =>
            timers.startSingleTimer(ExpireCheckout, ExpireCheckout, timerDuration)
            SelectingDelivery(timers)
        }

      case Cancelled(timers) =>
        event match {
          case CheckoutStarted =>
            timers.startSingleTimer(ExpireCheckout, ExpireCheckout, timerDuration)
            SelectingDelivery(timers)
        }
    }
  }
}
