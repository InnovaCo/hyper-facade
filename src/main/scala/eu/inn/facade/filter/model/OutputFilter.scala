package eu.inn.facade.filter.model

trait OutputFilter extends Filter {

  override def isInputFilter: Boolean = false
  override def isOutputFilter: Boolean = true
}
