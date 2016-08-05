package eu.inn.facade.filter.parser

case class Ip(ip: Long)

object Ip {
  def apply(ip: String): Ip = {
    var ipAddress: Long = 0
    val segments = ip.split('.').reverse
    for (i ← 3 to 0 by -1) {
      ipAddress += segments(i).toLong << (i * 8)
    }
    Ip(ipAddress)
  }
}

case class IpRange(from: Ip, to: Ip) {
  def contains(ip: Ip): Boolean = {
    val addr = ip.ip
    addr >= from.ip && addr <= to.ip
  }
}

object IpRange {
  def apply(from: String, to: String): IpRange = {
    IpRange(Ip(from), Ip(to))
  }

  def apply(net: String, mask: Long): IpRange = {
    val lowerBits = (1l << (32 - mask)) - 1
    val from = Ip(net).ip
    val to = from + lowerBits
    IpRange(Ip(from), Ip(to))
  }
}
