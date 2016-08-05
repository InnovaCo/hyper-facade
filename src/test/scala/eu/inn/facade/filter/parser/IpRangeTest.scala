package eu.inn.facade.filter.parser

import org.scalatest.{FreeSpec, Matchers}

class IpRangeTest extends FreeSpec with Matchers {

  "IpRange" - {
    "contains ip" in {
      IpRange("127.0.0.1", "128.0.0.1").contains(Ip("127.0.0.1")) shouldBe true
      IpRange("127.0.0.1", "128.0.0.1").contains(Ip("128.0.0.1")) shouldBe true
      IpRange("127.0.0.1", "128.0.0.1").contains(Ip("127.1.1.0")) shouldBe true
      IpRange("127.0.0.1", "128.0.0.1").contains(Ip("127.0.0.0")) shouldBe false
      IpRange("109.207.13.0", 24).contains(Ip("109.207.13.255")) shouldBe true
      IpRange("109.207.13.0", 24).contains(Ip("109.207.13.0")) shouldBe true
      IpRange("109.207.13.0", 24).contains(Ip("109.207.12.0")) shouldBe false
      IpRange("109.207.13.0", 24).contains(Ip("109.207.14.0")) shouldBe false
    }
  }
}
