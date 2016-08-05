package eu.inn.facade.filter.parser

import eu.inn.parser.HParser
import org.parboiled2._

import scala.util.{Failure, Success}

class IpParser(val input: ParserInput) extends Parser {
  import CharPredicate.{Digit, Visible}

  private def WhiteSpace = rule { zeroOrMore(HParser.WhiteSpaceChar) }
  private def IpOctet = rule { (1 to 3).times(Digit) }
  private def IpRule = rule { 4.times(IpOctet).separatedBy('.') }
  private def IpAddress = rule { WhiteSpace ~ capture (IpRule) ~ WhiteSpace ~> MakeIp }

  private def FromToIpRange = rule { capture(IpRule) ~ WhiteSpace ~ '-' ~ WhiteSpace ~ capture(IpRule) ~> MakeIpRange }
  private def SubnetIpRange = rule { (capture(IpRule) ~ WhiteSpace ~ '/' ~ WhiteSpace ~ capture((1 to 2).times(Digit))) ~> MakeSubnetIpRange }

  private def MakeIp = (ip: String) ⇒ Ip(ip)
  private def MakeIpRange = (from: String, to: String) ⇒ IpRange(from, to)
  private def MakeSubnetIpRange = (net: String, subnet: String) ⇒ IpRange(net, subnet.toLong)

  private def InRule = rule { capture(oneOrMore(Visible)) ~ WhiteSpace ~ "in" ~ WhiteSpace ~ capture(oneOrMore(ANY)) ~> ((l, r) ⇒ (l,"in",r)) }
  private def NotInRule = rule { capture(oneOrMore(Visible)) ~ WhiteSpace ~ "not in" ~ WhiteSpace ~ capture(oneOrMore(ANY)) ~> ((l, r) ⇒ (l,"not in",r)) }

  private def IpInputLine = rule { IpAddress ~ EOI }
  private def IpRangeInputLine = rule { (FromToIpRange | SubnetIpRange) ~ EOI }

  def IpInRangeInputLine = rule { (NotInRule | InRule) ~ EOI }
}

object IpParser {
  val IN_BOP = "in"
  val NOT_IN_BOP = "not in"

  def parseIp(expr: String): Option[Ip] = {
    IpParser(expr).IpInputLine.run() match {
      case Success(ip) ⇒ Some(ip)
      case Failure(_) ⇒ None
    }
  }

  def parseIpRange(expr: String): Option[IpRange] = {
    new IpParser(expr).IpRangeInputLine.run() match {
      case Success(range) ⇒
        Some(range)
      case Failure(_) ⇒
        None
    }
  }

  def apply(expr: String): IpParser = {
    new IpParser(expr)
  }
}

