package com.example

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.example.PokeRegistry.Populate
import com.example.domain.TwitterConf

import java.time.Duration
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object PokeApp {
  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start
    import system.executionContext

    val futureBinding = Http().newServerAt("localhost", 9000).bind(routes)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds))
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {
    sealed trait Command
    case object SetupCompleted extends Command
    case class SetupFailed(e: Throwable) extends Command

    val rootBehavior = Behaviors.setup[Command] { context =>

      implicit val scheduler = context.system.scheduler
      implicit val timeout = Timeout.create(Duration.ofMinutes(1))

      val pokeRegistryActor = context.spawn(PokeRegistry(), "PokeRegistryActor")

      context.watch(pokeRegistryActor)

      context.pipeToSelf(pokeRegistryActor.ask(Populate)) {
        case Success(_) => SetupCompleted
        case Failure(e) => SetupFailed(e)
      }

      def active = Behaviors.receiveMessage[Command] {
        case SetupCompleted =>
          val twitterConf = TwitterConf.fromConfig(context.system.settings.config)
          val twitterApi = new TwitterApi(twitterConf)(context.system)
          val pokeRoutes = new PokeRoutes(pokeRegistryActor, twitterApi)(context.system).pokemonRoutes
          startHttpServer(concat(pokeRoutes))(context.system)
          Behaviors.empty
        case SetupFailed(e) =>
          context.system.log.error(s"Fail to populate pokemon data, ${e.getMessage}")
          context.system.terminate()
          Behaviors.empty
      }
      active
    }
    val system = ActorSystem[Command](rootBehavior, "PokeToyHttpServer")
  }
}
