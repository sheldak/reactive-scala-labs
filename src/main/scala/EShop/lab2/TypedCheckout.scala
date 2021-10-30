package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import scala.language.postfixOps

import scala.concurrent.duration._
import EShop.lab3.{OrderManager, Payment}

object TypedCheckout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(
    payment: String,
    managerCheckoutMapper: ActorRef[Event],
    managerPaymentMapper: ActorRef[Payment.Event]
  ) extends Command
  case object ExpirePayment          extends Command
  case object ConfirmPaymentReceived extends Command

  sealed trait Event
  case object CheckoutClosed                                       extends Event
  case object CheckoutCancelled                                    extends Event
  case class PaymentStarted(paymentRef: ActorRef[Payment.Command]) extends Event

  def apply(cartActor: ActorRef[TypedCheckout.Event]): Behavior[Command] =
    Behaviors.setup(context => new TypedCheckout(cartActor).start)
}

class TypedCheckout(
  cartActor: ActorRef[TypedCheckout.Event]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  var paymentMapper: ActorRef[Payment.Event] = null

  def start: Behavior[TypedCheckout.Command] = Behaviors.setup { context =>
    paymentMapper = context.messageAdapter(
      event =>
        event match {
          case Payment.PaymentReceived => ConfirmPaymentReceived
      }
    )

    Behaviors.withTimers(timers => start(timers))
  }

  def start(timers: TimerScheduler[TypedCheckout.Command]): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case StartCheckout =>
          timers.startSingleTimer(ExpireCheckout, ExpireCheckout, checkoutTimerDuration)
          selectingDelivery(timers)
    }
  )

  def selectingDelivery(timers: TimerScheduler[TypedCheckout.Command]): Behavior[TypedCheckout.Command] =
    Behaviors.receive(
      (context, msg) =>
        msg match {
          case SelectDeliveryMethod(method) =>
            selectingPaymentMethod(timers)

          case CancelCheckout =>
            timers.cancel(ExpireCheckout)
            cancelled

          case ExpireCheckout =>
            cancelled
      }
    )

  def selectingPaymentMethod(timers: TimerScheduler[TypedCheckout.Command]): Behavior[TypedCheckout.Command] =
    Behaviors.receive(
      (context, msg) =>
        msg match {
          case SelectPayment(payment, managerCheckoutMapper, managerPaymentMapper) =>
            timers.cancel(ExpireCheckout)
            timers.startSingleTimer(ExpirePayment, ExpirePayment, paymentTimerDuration)

            val paymentRef = context.spawn(Payment(payment, managerPaymentMapper, paymentMapper), "payment")
            managerCheckoutMapper ! PaymentStarted(paymentRef)

            processingPayment(timers)

          case CancelCheckout =>
            timers.cancel(ExpireCheckout)
            cancelled

          case ExpireCheckout =>
            cancelled
      }
    )

  def processingPayment(timers: TimerScheduler[TypedCheckout.Command]): Behavior[TypedCheckout.Command] =
    Behaviors.receive(
      (context, msg) =>
        msg match {
          case ConfirmPaymentReceived =>
            timers.cancel(ExpirePayment)
            cartActor ! CheckoutClosed
            closed

          case CancelCheckout =>
            timers.cancel(ExpirePayment)
            cartActor ! CheckoutCancelled
            cancelled

          case ExpirePayment =>
            cartActor ! CheckoutCancelled
            cancelled
      }
    )

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.stopped

  def closed: Behavior[TypedCheckout.Command] = Behaviors.stopped
}
