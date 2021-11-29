package com.example

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.example.PokeAPI.extractPokemonId
import com.example.domain.Page
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.Future


class PokeAPI(implicit val system:ActorSystem) {

  import system.dispatcher
  private implicit val timeout = Timeout.create(system.settings.config.getDuration("my-app.pokeApi.timeout"))

  val http = Http()

  private def makeUri(path: String) = s"https://pokeapi.co/api/v2/$path"

  private def unmarshalOptionalJson[T](future: Future[HttpResponse])(implicit unmarshaller: Unmarshaller[HttpResponse, Option[T]]) =
    future.flatMap {
      case response @ HttpResponse(StatusCodes.OK, _, _, _) =>
        Unmarshal(response).to[Option[T]]
      case _ =>
        Future.successful(Option.empty)
    }

  def getPokemons(page: Page): Future[Option[JsObject]] =
    unmarshalOptionalJson[JsObject](http.singleRequest(HttpRequest(uri = makeUri(s"pokemon?limit=${page.limit}&offset=${page.offset}"))))

  def getAllPokemons: Source[JsObject, NotUsed] =
    Page.zero().fetchAll { page =>
      getPokemons(page)
        .map(_
          .map(_.asJsObject().fields)
          .map { fields =>
            val hasNext = fields.get("next").exists(_ != JsNull)
            val results = fields.get("results")
              .map(_.convertTo[List[JsObject]])
              .getOrElse(List())
            (results, hasNext)
          }
          .getOrElse((List(), false))
        )
    }.mapAsync(4) { js =>
      js.fields.get("url").map(_.convertTo[String]).map(extractPokemonId)
        .map(id => getPokemon(id.toString))
        .getOrElse(Future.successful(Option.empty))
    }
      .filter(_.isDefined)
      .map(_.get)

  def getPokemon(name: String): Future[Option[JsObject]] = {
    unmarshalOptionalJson[JsObject](http.singleRequest(HttpRequest(uri = makeUri(s"pokemon/$name"))))
  }
}

object PokeAPI {
  def extractId(entity: String)(url: String): Int = {
    val pattern = s"""https://pokeapi.co/api/v2/${entity}/(\\d+)/""".r
    val pattern(id) = url
    id.toInt
  }

  def extractPokemonId(url: String): Int = extractId("pokemon")(url)
  def extractStatId(url: String): Int = extractId("stat")(url)
  def extractTypeId(url: String): Int = extractId("type")(url)

}
