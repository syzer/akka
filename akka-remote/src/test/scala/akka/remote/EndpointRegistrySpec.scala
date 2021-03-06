package akka.remote

import akka.testkit.AkkaSpec
import akka.actor.{ Props, ActorRef, Address }
import akka.remote.EndpointManager._
import scala.concurrent.duration._

class EndpointRegistrySpec extends AkkaSpec {

  val actorA = system.actorOf(Props.empty, "actorA")
  val actorB = system.actorOf(Props.empty, "actorB")

  val address1 = Address("test", "testsys1", "testhost1", 1234)
  val address2 = Address("test", "testsys2", "testhost2", 1234)

  "EndpointRegistry" must {

    "be able to register a writable endpoint and policy" in {
      val reg = new EndpointRegistry

      reg.writableEndpointWithPolicyFor(address1) should equal(None)

      reg.registerWritableEndpoint(address1, actorA) should equal(actorA)

      reg.writableEndpointWithPolicyFor(address1) should equal(Some(Pass(actorA)))
      reg.readOnlyEndpointFor(address1) should equal(None)
      reg.isWritable(actorA) should be(true)
      reg.isReadOnly(actorA) should be(false)

      reg.isQuarantined(address1, 42) should be(false)
    }

    "be able to register a read-only endpoint" in {
      val reg = new EndpointRegistry
      reg.readOnlyEndpointFor(address1) should equal(None)

      reg.registerReadOnlyEndpoint(address1, actorA) should equal(actorA)

      reg.readOnlyEndpointFor(address1) should equal(Some(actorA))
      reg.writableEndpointWithPolicyFor(address1) should equal(None)
      reg.isWritable(actorA) should be(false)
      reg.isReadOnly(actorA) should be(true)
      reg.isQuarantined(address1, 42) should be(false)
    }

    "be able to register a writable and a read-only endpoint correctly" in {
      val reg = new EndpointRegistry
      reg.readOnlyEndpointFor(address1) should equal(None)
      reg.writableEndpointWithPolicyFor(address1) should equal(None)

      reg.registerReadOnlyEndpoint(address1, actorA) should equal(actorA)
      reg.registerWritableEndpoint(address1, actorB) should equal(actorB)

      reg.readOnlyEndpointFor(address1) should equal(Some(actorA))
      reg.writableEndpointWithPolicyFor(address1) should equal(Some(Pass(actorB)))

      reg.isWritable(actorA) should be(false)
      reg.isWritable(actorB) should be(true)

      reg.isReadOnly(actorA) should be(true)
      reg.isReadOnly(actorB) should be(false)

    }

    "be able to register Gated policy for an address" in {
      val reg = new EndpointRegistry

      reg.writableEndpointWithPolicyFor(address1) should equal(None)
      reg.registerWritableEndpoint(address1, actorA)
      val deadline = Deadline.now
      reg.markAsFailed(actorA, deadline)
      reg.writableEndpointWithPolicyFor(address1) should equal(Some(Gated(deadline)))
      reg.isReadOnly(actorA) should be(false)
      reg.isWritable(actorA) should be(false)
    }

    "remove read-only endpoints if marked as failed" in {
      val reg = new EndpointRegistry

      reg.registerReadOnlyEndpoint(address1, actorA)
      reg.markAsFailed(actorA, Deadline.now)
      reg.readOnlyEndpointFor(address1) should equal(None)
    }

    "keep tombstones when removing an endpoint" in {
      val reg = new EndpointRegistry

      reg.registerWritableEndpoint(address1, actorA)
      reg.registerWritableEndpoint(address2, actorB)
      val deadline = Deadline.now
      reg.markAsFailed(actorA, deadline)
      reg.markAsQuarantined(address2, 42, deadline)

      reg.unregisterEndpoint(actorA)
      reg.unregisterEndpoint(actorB)

      reg.writableEndpointWithPolicyFor(address1) should equal(Some(Gated(deadline)))
      reg.writableEndpointWithPolicyFor(address2) should equal(Some(Quarantined(42, deadline)))

    }

    "prune outdated Gated directives properly" in {
      val reg = new EndpointRegistry

      reg.registerWritableEndpoint(address1, actorA)
      reg.registerWritableEndpoint(address2, actorB)
      reg.markAsFailed(actorA, Deadline.now)
      val farInTheFuture = Deadline.now + Duration(60, SECONDS)
      reg.markAsFailed(actorB, farInTheFuture)
      reg.prune()

      reg.writableEndpointWithPolicyFor(address1) should equal(None)
      reg.writableEndpointWithPolicyFor(address2) should equal(Some(Gated(farInTheFuture)))
    }

    "be able to register Quarantined policy for an address" in {
      val reg = new EndpointRegistry
      val deadline = Deadline.now + 30.minutes

      reg.writableEndpointWithPolicyFor(address1) should equal(None)
      reg.markAsQuarantined(address1, 42, deadline)
      reg.isQuarantined(address1, 42) should be(true)
      reg.isQuarantined(address1, 33) should be(false)
      reg.writableEndpointWithPolicyFor(address1) should equal(Some(Quarantined(42, deadline)))
    }

  }

}
