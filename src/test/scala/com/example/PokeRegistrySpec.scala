package com.example

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import com.example.PokeRegistry.{CompareStats, GetPokemons, Populate}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Minute, Span}
import org.scalatest.wordspec.AnyWordSpec
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject

import java.time.Duration

class PokeRegistrySpec extends AnyWordSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  lazy val testKit = ActorTestKit()
  implicit def typedSystem = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.classicSystem
  private implicit val timeout = Timeout.create(Duration.ofMinutes(1))
  private implicit val scheduler = testKit.system.scheduler
  override implicit val patienceConfig = PatienceConfig(Span(1, Minute))

  val pokemonRegistry = testKit.spawn(PokeRegistry())
  pokemonRegistry.ask(Populate)
    .futureValue

  "PokeRegistry" should {
    "find pokemons by name" in {
      val pokemon = pokemonRegistry.ask(GetPokemons("pikachu", _))
        .futureValue
      pokemon should not be empty
    }

    "compare stats" in {
      val pikachu = pokemonRegistry.ask(GetPokemons("pikachu", _))
        .futureValue
        .headOption

      val id = pikachu.flatMap(_.fields.get("id").map(_.convertTo[Int]))
      val stats = pikachu.flatMap(_.fields.get("stats").map(_.convertTo[Seq[JsObject]])).getOrElse(Seq())
      id should not be empty
      stats should not be empty
      val baseStats = pokemonRegistry.ask(CompareStats(id.get, _))
        .futureValue
      baseStats should not be empty
    }

  }
}