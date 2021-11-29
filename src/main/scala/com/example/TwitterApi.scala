package com.example

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Source
import com.example.domain.{TwitterConf, Utils}
import io.lemonlabs.uri.AbsoluteUrl
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import scala.collection.SortedMap
import scala.util.Random

class TwitterApi(conf: TwitterConf)(implicit system: ActorSystem[_]) {

  import system.executionContext

  implicit val jsonStreamingSupport: JsonEntityStreamingSupport =
    EntityStreamingSupport.json(32 * 1024)

  def signBearer(request: HttpRequest) =
    request.addHeader(Authorization(OAuth2BearerToken(conf.token)))

  def signOauth(request: HttpRequest) = {
    val oauthTimestamp = System.currentTimeMillis() / 1000
    val oauthNonce = oauthTimestamp + new Random().nextInt()

    val oauthParameters = Map[String, String](
      "oauth_nonce" -> oauthNonce.toString,
      "oauth_timestamp" -> oauthTimestamp.toString,
      "oauth_consumer_key" -> conf.key,
      "oauth_token" -> conf.token,
      "oauth_signature_method" -> "HMAC-SHA1",
      "oauth_version" -> "1.0"
    )

    val url = AbsoluteUrl.parse(request.uri.toString())
    val uri = s"${url.scheme}://${url.host}${url.path}"
    val query = url.query.paramMap.view.mapValues(_.head).toMap

    val params = SortedMap((oauthParameters ++ query).toList:_*)
      .map { case (k,v) => s"${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}" }
      .mkString("&")
    val toSign = s"${request.method.value.toUpperCase}&${URLEncoder.encode(uri, StandardCharsets.UTF_8)}&${URLEncoder.encode(params, StandardCharsets.UTF_8)}"
    val signed = Utils.getHash(toSign, conf.secret, conf.tokenSecret)

    request.addHeader(Authorization(GenericHttpCredentials("OAuth", oauthParameters + (("oauth_signature", URLEncoder.encode(signed, StandardCharsets.UTF_8))))))
  }

  def track(keyword: String): Source[JsObject, Any] = Source.futureSource(
    Http().singleRequest(signOauth(HttpRequest(uri = s"https://stream.twitter.com/1.1/statuses/filter.json?track=${keyword}")))
      .map { response =>
        response.status match {
          case OK =>
            response.entity.withoutSizeLimit().dataBytes
              .via(jsonStreamingSupport.framingDecoder)
              .mapAsync(1)(bytes => Unmarshal(bytes).to[JsObject])
          case other =>
            response.entity.dataBytes.map(_.utf8String)
              .flatMapConcat(error => Source.failed(new RuntimeException(error)))
        }
      }
  )
}
