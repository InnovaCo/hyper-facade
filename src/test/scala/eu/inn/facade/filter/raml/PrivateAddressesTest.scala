package eu.inn.facade.filter.raml

import org.scalatest.{FreeSpec, Matchers}

class PrivateAddressesTest extends FreeSpec with Matchers {

  "PrivateAddresses" - {
    "isAllowedAddress" in {
      var privateAddresses = PrivateAddresses(
        Seq("127.0.0.1", "127.0.0.2"),
        Seq(NetworkRange("1.1.1.1", "126.255.255.255"),
          NetworkRange("192.168.0.1", "192.168.255.255")))

      var ip = "127.0.0.1"
      assert(privateAddresses.isAllowedAddress(ip))

      ip = "127.0.0.3"
      assert(!privateAddresses.isAllowedAddress(ip))

      ip = "192.168.125.54"
      assert(privateAddresses.isAllowedAddress(ip))

      ip = "192.169.0.0"
      assert(!privateAddresses.isAllowedAddress(ip))

      privateAddresses = PrivateAddresses(Seq.empty, Seq.empty)
      assert(!privateAddresses.isAllowedAddress(ip))

      privateAddresses = PrivateAddresses(
        Seq.empty,
        Seq(NetworkRange("0.0.0.0", "255.255.255.255")))

      assert(privateAddresses.isAllowedAddress(ip))
    }
  }
}
