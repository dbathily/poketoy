package com.example

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.example.PokeRegistry.{CompareStats, GetPokemon, GetPokemons}
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import scala.concurrent.Future

class PokeRoutes(pokeRegistry: ActorRef[PokeRegistry.Command], twitterApi: TwitterApi)(implicit val system: ActorSystem[_]) {

  import JsonFormats._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  // If ask takes more time than this to complete the request is failed
  private implicit val timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))
  implicit val ec = system.executionContext

  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()

  def getPokemons(name: String): Future[Seq[JsObject]] =
    pokeRegistry.ask(GetPokemons(name, _))
  def getPokemon(id: Int): Future[Option[JsObject]] =
    pokeRegistry.ask(GetPokemon(id, _))
  def comparePokemonStat(id: Int): Future[Seq[TypeBaseStatsResume]] =
    pokeRegistry.ask(CompareStats(id, _))
  def getTweets(id: Int): Source[ServerSentEvent, Any] = Source.futureSource(
    getPokemon(id).map(_.flatMap(_.fields.get("name")).map(_.convertTo[String]).map(name => twitterApi.track(name)).getOrElse(Source.empty))
  ).map(js => ServerSentEvent(js.toString()))

  val pokemonRoutes: Route = {
    concat(
      pathPrefix("pokemons") {
        concat(
          path(Segment) { name =>
            concat(
              get {
                rejectEmptyResponse {
                  onSuccess(getPokemons(name)) { response =>
                    complete(response)
                  }
                }
              })
          })
      },
      pathPrefix("pokemon" / IntNumber) { id =>
        concat(
          pathEnd {
            get {
              rejectEmptyResponse {
                onSuccess(getPokemon(id)) { response =>
                  complete(response)
                }
              }
            }
          },
          path("compare") {
            get {
              complete(comparePokemonStat(id))
            }
          },
          path("tweets") {
            get {
              complete(getTweets(id))
            }
          }
        )
      }
    )
  }
}
