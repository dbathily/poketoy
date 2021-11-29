package com.example

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.scaladsl.Sink
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject

import scala.collection.mutable
import scala.util.{Failure, Success, Try}


case class BaseStatsResume(id: Int, url: String, total: Int, count: Int) {
  def inc(stat: Int): BaseStatsResume = copy(total = total + stat, count = count + 1)
}
case class TypeBaseStatsResume(id:Int, url: String, stats: Seq[BaseStatsResume])

object PokeRegistry {

  sealed trait Command
  final case class Populated() extends Command
  final case class Populate(replyTo: ActorRef[Populated]) extends Command
  final case class GetPokemons(name: String, replyTo: ActorRef[Seq[JsObject]]) extends Command
  final case class GetPokemon(id: Int, replyTo: ActorRef[Option[JsObject]]) extends Command
  final case class CompareStats(id: Int, replyTo: ActorRef[Seq[TypeBaseStatsResume]]) extends Command

  private case class PopulateComplete(list: Seq[JsObject]) extends Command
  private case class WaitingComplete[T](result: Try[T]) extends Command

  private def getTypes(pokemon: JsObject) =
    pokemon.fields.get("types").map(_.convertTo[Seq[JsObject]].flatMap(_.fields.get("type").flatMap(_.convertTo[JsObject].fields.get("url").map(_.convertTo[String]))))

  def apply(stashCapacity: Int = 100): Behavior[Command] =
    Behaviors.withStash(stashCapacity) { buffer =>
      Behaviors.setup { context =>
        implicit val system = context.system.classicSystem
        import system.dispatcher

        val api = new PokeAPI

        def populating(replyTo: ActorRef[Populated]): Behavior[Command] = Behaviors.receiveMessage {
          case PopulateComplete(list) =>
            replyTo ! Populated()
            buffer.unstashAll(active(list))
          case other =>
            buffer.stash(other)
            Behaviors.same
        }

        def active(list: Seq[JsObject]): Behavior[Command] = Behaviors.receiveMessagePartial {
          case Populate(replyTo) =>
            api.getAllPokemons
              .runWith(Sink.seq)
              .onComplete {
                case Success(value) => context.self ! PopulateComplete(value)
                case Failure(exception) =>
                  context.log.warn(exception.getMessage)
                  context.self ! PopulateComplete(Seq())
              }
            populating(replyTo)
          case GetPokemons(name, replyTo) =>
            replyTo ! list.filter(p => p.fields.get("name").exists(_.convertTo[String].contains(name)))
            Behaviors.same
          case GetPokemon(id, replyTo) =>
            replyTo ! list.find(_.fields.get("id").exists(_.convertTo[Int] == id))
            Behaviors.same
          case CompareStats(id, replyTo) =>
            val stats = list.find(_.fields.get("id").exists(_.convertTo[Int] == id))
              .flatMap(getTypes)
              .map { types =>
                types.map { t => TypeBaseStatsResume(
                  PokeAPI.extractTypeId(t),
                  t,
                  list.filter(p => getTypes(p).getOrElse(Seq()).contains(t))
                    .foldLeft(mutable.HashMap[String, (Int, Int)]()) { (acc, pokemon) =>
                      pokemon.fields.get("stats").map(_.convertTo[Seq[JsObject]].flatMap { stat =>
                        for (
                          url <- stat.fields.get("stat").flatMap(_.convertTo[JsObject].fields.get("url").map(_.convertTo[String]));
                          baseStat <- stat.fields.get("base_stat").map(_.convertTo[Int])
                        ) yield (url, baseStat)
                      }).getOrElse(Seq())
                        .foreach { case (url, stat) =>
                          acc.updateWith(url)(v => Option(v.map(e => (e._1 + stat, e._2 + 1)).getOrElse((stat, 1))))
                        }
                      acc
                    }
                    .map { case (key, (total, count)) => BaseStatsResume(PokeAPI.extractStatId(key), key, total, count) }
                    .toSeq
                )}
              }.getOrElse(Seq())

            replyTo ! stats
            Behaviors.same
        }

        active(Seq())
      }
    }
}
