package com.example

import spray.json.DefaultJsonProtocol

object JsonFormats  {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val baseStatsResumeJsonFormat = jsonFormat4(BaseStatsResume)
  implicit val typeBaseStatsResumeJsonFormat = jsonFormat3(TypeBaseStatsResume)
}
