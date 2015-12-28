package eu.inn.facade.http

import java.lang.reflect.{ParameterizedType, Type}

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser.Feature
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MappingJsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule

trait JsonSupport extends FormatSupport {
  def serialize(value: Any): String =
    JsonSupport.mapper.writeValueAsString(value)

  def serializePretty(value: Any): String =
    JsonSupport.mapper.writer.withDefaultPrettyPrinter().writeValueAsString(value)

  def deserialize[T: Manifest](value: String): T =
    JsonSupport.mapper.readValue(value, typeReference[T])
}


object JsonSupport extends JsonSupport {
  val mapper = createMapper()

  val factory = new MappingJsonFactory(mapper)

  def createMapper() = {
    val m = new ObjectMapper
    m.registerModule(DefaultScalaModule)
    m.registerModule(new JodaModule)
    m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    m.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
    m.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
    m.configure(Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true)
    m.configure(Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
    m.configure(Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
    m.configure(Feature.ALLOW_SINGLE_QUOTES, true)
    m.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, true)
    m.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false)
    m.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
    m.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    m.setSerializationInclusion(JsonInclude.Include.NON_NULL)
    m
  }
}

trait FormatSupport {

  def serialize(value: Any): String

  def deserialize[T: Manifest](value: String): T

  def deserialize[T: Manifest](value: Array[Byte]): T =
    deserialize[T](new String(value))

  def deserialize[T: Manifest](value: String, clazz: Class[T]): T =
    deserialize(value)

  def deserialize[T: Manifest](value: Array[Byte], clazz: Class[T]): T =
    deserialize(value)

  def typeReference[T: Manifest] = new TypeReference[T] {
    override def getType = typeFromManifest(manifest[T])
  }

  private[http] def typeFromManifest(m: Manifest[_]): Type =
    if (m.typeArguments.isEmpty) {
      m.runtimeClass
    } else {
      new ParameterizedType {
        def getRawType = m.runtimeClass
        def getActualTypeArguments = m.typeArguments.map(typeFromManifest).toArray
        def getOwnerType = null
      }
    }
}
