package eu.inn.facade.utils.raml

import javax.script.ScriptEngine

import com.mulesoft.raml.webpack.holders.AbstractJSWrapper
import org.slf4j.LoggerFactory


class JsToLogConsole(engine: ScriptEngine) extends AbstractJSWrapper(engine) {
  val log = LoggerFactory.getLogger(getClass)
  def log(text: String) {
    log.debug(text)
  }

  def warn(text: String) {
    log.warn(text)
  }

  def getClassName: String = {
    "Console"
  }
}
