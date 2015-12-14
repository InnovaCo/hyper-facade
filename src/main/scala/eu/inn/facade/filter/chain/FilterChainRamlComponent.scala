package eu.inn.facade.filter.chain

import eu.inn.util.ConfigComponent

trait FilterChainRamlComponent extends FilterChainComponent with ConfigComponent {

   override def filterChain(uri: String): FilterChain = {
      val filterTraits = extractTraitsFromRaml(uri)
      constructFilterChain(filterTraits)
   }

   def extractTraitsFromRaml(uri: String): Seq[String] = ???
   def constructFilterChain(filterTraits: Seq[String]): FilterChain = ???
}
