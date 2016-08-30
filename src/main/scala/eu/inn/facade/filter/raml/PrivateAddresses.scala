package eu.inn.facade.filter.raml

case class PrivateAddresses(ipAddresses: Seq[String], networks: Seq[NetworkRange]) {
  def isAllowedAddress(ip: String): Boolean = {
    var isAllowed = ipAddresses.contains(ip)
    if (!isAllowed) {
      val ipLong = IpAddress.ipToLong(ip)
      networks foreach { networkRange ⇒
        isAllowed |= networkRange.contains(ipLong)
      }
    }
    isAllowed
  }
}

case class NetworkRange(from: Long, to: Long) {
  def contains(ip: Long): Boolean = {
    ip >= from && ip <= to
  }
}

object NetworkRange {
  def apply(from: String, to: String): NetworkRange = {
    NetworkRange(IpAddress.ipToLong(from), IpAddress.ipToLong(to))
  }
}

object IpAddress {
  def ipToLong(ip: String): Long = {
    var ipAddress: Long = 0
    val segments = ip.split('.').reverse
    for (i ← 3 to 0 by -1) {
      ipAddress += segments(i).toLong << (i * 8)
    }
    ipAddress
  }

  def isIpAddress(value: String): Boolean = {
    true
  }
}
