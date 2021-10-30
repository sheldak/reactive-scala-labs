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
    val managerCheckoutMapperProbe = testKit.createTestProbe[TypedCheckout.Event]()
    val managerPaymentMapperProbe  = testKit.createTestProbe[Payment.Event]()

    val checkout = testKit.spawn(TypedCheckout(managerCheckoutMapperProbe.ref), "checkout")

    checkout ! TypedCheckout.StartCheckout
    checkout ! TypedCheckout.SelectDeliveryMethod("post")
    checkout ! TypedCheckout.SelectPayment("paypal", managerCheckoutMapperProbe.ref, managerPaymentMapperProbe.ref)

    val paymentStarted = managerCheckoutMapperProbe.expectMessageType[TypedCheckout.PaymentStarted]

    paymentStarted.paymentRef ! Payment.DoPayment
    managerPaymentMapperProbe.expectMessage(Payment.PaymentReceived)

    managerCheckoutMapperProbe.expectMessage(TypedCheckout.CheckoutClosed)
  }
}
