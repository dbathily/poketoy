package com.example

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Sink
import com.example.domain.Page
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Minute, Minutes, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import spray.json.DefaultJsonProtocol._
import spray.json.{JsNull, _}

class PokeAPISpec extends AnyWordSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  lazy val testKit = ActorTestKit()
  implicit def typedSystem = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.classicSystem

  override implicit val patienceConfig = PatienceConfig(Span(2, Minutes))

  val pokeAPI = new PokeAPI

  "PokeAPI" should {
    "extract id" in {
      val id = PokeAPI.extractId("pokemon")("https://pokeapi.co/api/v2/pokemon/12/")
      id should be (12)
    }

    "list pokemons" in {
      val pokemons = pokeAPI.getPokemons(Page.zero(10)).futureValue
      pokemons should not be empty
      val js = pokemons.get
      js.fields.get("count") should not be empty
      js.fields.get("results") should not be empty
      js.fields.get("next") should not be(Some(JsNull))
    }

    "list empty pokemons" in {
      val pokemons = pokeAPI.getPokemons(Page(10, 10000)).futureValue
      pokemons should not be empty
      val js = pokemons.get
      js.fields.get("count") should not be empty
      js.fields.get("results") should not be empty
      js.fields.get("next") should be(Some(JsNull))
    }

    "fetch all pokemons" in {
      val pokemons = pokeAPI.getPokemons(Page.zero(1)).futureValue
      pokemons should not be empty

      val count = pokeAPI.getAllPokemons
        .runWith(Sink.fold(0) { case (acc, _) => acc + 1}).futureValue

      pokemons should not be empty
      pokemons.get.fields.get("count").map(_.convertTo[Int]) should be(Some(count))
    }


    "return a pokemon" in {
      val pokemon = pokeAPI.getPokemon("pikachu").futureValue
      pokemon should not be empty
    }
    "return an empty pokemon if not exist" in {
      val pokemon = pokeAPI.getPokemon("didier").futureValue
      pokemon should be(empty)
    }
  }
}
