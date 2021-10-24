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
  case object StartCheckout                                                                  extends Command
  case class SelectDeliveryMethod(method: String)                                            extends Command
  case object CancelCheckout                                                                 extends Command
  case object ExpireCheckout                                                                 extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ExpirePayment                                                                  extends Command
  case object ConfirmPaymentReceived                                                         extends Command

  sealed trait Event
  case object CheckOutClosed                           extends Event
  case class PaymentStarted(paymentRef: ActorRef[Any]) extends Event

  def apply(cartActor: ActorRef[TypedCartActor.Command]): Behavior[Command] =
    Behaviors.setup(context => new TypedCheckout(cartActor).start)
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] = Behaviors.withTimers(timers => start(timers))

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
          case SelectPayment(payment, orderManagerRef) =>
            timers.cancel(ExpireCheckout)
            timers.startSingleTimer(ExpirePayment, ExpirePayment, paymentTimerDuration)

            val paymentRef = context.spawn(Payment(payment, orderManagerRef, context.self), "payment")
            orderManagerRef ! OrderManager.ConfirmPaymentStarted(paymentRef)

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
            cartActor ! TypedCartActor.ConfirmCheckoutClosed
            closed

          case CancelCheckout =>
            timers.cancel(ExpirePayment)
            cartActor ! TypedCartActor.ConfirmCheckoutCancelled
            cancelled

          case ExpirePayment =>
            cartActor ! TypedCartActor.ConfirmCheckoutCancelled
            cancelled
      }
    )

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.stopped

  def closed: Behavior[TypedCheckout.Command] = Behaviors.stopped
}
