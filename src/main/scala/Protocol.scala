package org.tmsrv.play.gelf

import play.api.libs.json._


object GELFVersion extends Enumeration {
  val V1_0 = Value("1.0")
  val V1_1 = Value("1.1")
}

object GELFProtocol {
  private def enumFormat[T <: Enumeration](e: T) = new Format[T#Value] {
    def reads(json: JsValue) = e.values.find( _.toString == json.as[String] ) match {
      case Some(value) =>
        JsSuccess(value)
      case None =>
        JsError("Invalid enumeration value: %s".format(json.as[String]))
    }
    def writes(value: T#Value) = JsString(value.toString)
  }

  private implicit val GELFVersionFormat = enumFormat[GELFVersion.type](GELFVersion)

  private val NULL = new JsString("null")
  private val TRUE = new JsString("true")
  private val FALSE = new JsString("false")


  def build(version: GELFVersion.Value, host: String, timestamp: Long, shortMessage: String, fullMessage: Option[String], fields: Option[JsObject], level: Option[Int], facility: Option[Int], file: Option[String], line: Option[Int]) = JsObject(
    Map(
      "version" -> Some(Json.toJson(version)),
      "host" -> Some(Json.toJson(host)),
      "short_message" -> Some(Json.toJson(shortMessage)),
      "full_message" -> fullMessage.map( x => Json.toJson(x) ),
      "timestamp" -> Some(Json.toJson(BigDecimal(timestamp) / 1000)),
      "level" -> level.map( x => Json.toJson(x) ),
      "facility" -> facility.map( x => Json.toJson(x) ),
      "line" -> line.map( x => Json.toJson(x) ),
      "file" -> file.map( x => Json.toJson(x) )
    )
      .filter({ case (key, value) => value.isDefined })
      .map({ case (key, value) => key -> value.get })
  ) ++ JsObject(
      fields.getOrElse(Json.obj())
        .value
        .filter({ case (key, value) => key != "id" })
        .flatMap({ case (key, value) => flattenJson(key, value) })
        .map({ case (key, value) => ("_" + key) -> value })
  )

  private def flattenJson(key: String, value: JsValue): Seq[(String, JsValue)] = value match {
    case JsNull =>
      Seq(key -> NULL)
    case value: JsBoolean =>
      Seq(key -> (if (value.value) TRUE else FALSE))
    case value: JsNumber =>
      Seq(key -> value)
    case value: JsString =>
      Seq(key -> value)
    case value: JsArray =>
      value.value.zipWithIndex.flatMap({ case (child, i) => flattenJson(key + "_" + i, child) })
    case value: JsObject =>
      value.value.toSeq.flatMap({ case (childKey, child) => flattenJson(key + "_" + childKey, child) })
  }
}
