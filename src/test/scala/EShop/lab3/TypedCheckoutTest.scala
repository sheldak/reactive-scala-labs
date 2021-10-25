package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  import TypedCheckout._

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    val cartProbe    = testKit.createTestProbe[TypedCartActor.Command]()
    val managerProbe = testKit.createTestProbe[OrderManager.Command]()

    val checkout = testKit.spawn(TypedCheckout(cartProbe.ref), "checkout")

    checkout ! TypedCheckout.StartCheckout
    checkout ! TypedCheckout.SelectDeliveryMethod("post")
    checkout ! TypedCheckout.SelectPayment("paypal", managerProbe.ref)

    val paymentStarted = managerProbe.expectMessageType[OrderManager.ConfirmPaymentStarted]

    paymentStarted.paymentRef ! Payment.DoPayment
    managerProbe.expectMessage(OrderManager.ConfirmPaymentReceived)

    cartProbe.expectMessage(TypedCartActor.ConfirmCheckoutClosed)
  }
}
