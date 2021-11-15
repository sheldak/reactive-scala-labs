package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object OrderManager {

  sealed trait Command
  case class AddItem(id: String, sender: ActorRef[Ack])                                               extends Command
  case class RemoveItem(id: String, sender: ActorRef[Ack])                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command
  case class Buy(sender: ActorRef[Ack])                                                               extends Command
  case class Pay(sender: ActorRef[Ack])                                                               extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef[Payment.Command])                             extends Command
  case object ConfirmPaymentReceived                                                                  extends Command
  case object ConfirmCheckoutClosed                                                                   extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class OrderManager {

  import OrderManager._

  var cartMapper: ActorRef[TypedCartActor.Event]    = null
  var checkoutMapper: ActorRef[TypedCheckout.Event] = null
  var paymentMapper: ActorRef[Payment.Event]        = null

  def start: Behavior[OrderManager.Command] = Behaviors.setup { context =>
    cartMapper = context.messageAdapter(
      event =>
        event match {
          case TypedCartActor.CheckoutStarted(checkoutRef) => ConfirmCheckoutStarted(checkoutRef)
      }
    )

    checkoutMapper = context.messageAdapter(
      event =>
        event match {
          case TypedCheckout.CheckoutClosed             => ConfirmCheckoutClosed
          case TypedCheckout.PaymentStarted(paymentRef) => ConfirmPaymentStarted(paymentRef)
      }
    )

    paymentMapper = context.messageAdapter(
      event =>
        event match {
          case Payment.PaymentReceived => ConfirmPaymentReceived
      }
    )

    val cartActor = context.spawn(TypedCartActor(), "cart")
    open(cartActor)
  }

  def uninitialized: Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) => Behaviors.same
  )

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case AddItem(id, sender) =>
          cartActor ! TypedCartActor.AddItem(id)
          sender ! Done
          Behaviors.same

        case RemoveItem(id, sender) =>
          cartActor ! TypedCartActor.RemoveItem(id)
          sender ! Done
          Behaviors.same

        case Buy(sender) =>
          cartActor ! TypedCartActor.StartCheckout(cartMapper)
          inCheckout(cartActor, sender)
    }
  )

  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case ConfirmCheckoutStarted(checkoutRef) =>
          senderRef ! Done
          inCheckout(checkoutRef)
    }
  )

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
          checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
          checkoutActorRef ! TypedCheckout.SelectPayment(payment, checkoutMapper, paymentMapper)
          inPayment(sender)
    }
  )

  def inPayment(senderRef: ActorRef[Ack]): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case ConfirmPaymentStarted(paymentRef) =>
          senderRef ! Done
          inPayment(paymentRef, senderRef)

        case ConfirmPaymentReceived =>
          senderRef ! Done
          finished
    }
  )

  def inPayment(
    paymentActorRef: ActorRef[Payment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case Pay(sender) =>
          paymentActorRef ! Payment.DoPayment
          inPayment(sender)
    }
  )

  def finished: Behavior[OrderManager.Command] = Behaviors.stopped
}
