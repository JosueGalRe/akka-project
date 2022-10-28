package dev.galre.josue.akkaProject

import controller.MainRouter
import repository.{ GameManagerActor, ReviewManagerActor, UserManagerActor }
import service.{ SteamManagerReader, SteamManagerWriter }

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object Application {

  // TODO: Implement CQRS (Sebastian suggested to wait until module 9 to implement this, along with other techniques)
  // TODO: Move actor creation to separated file in utils
  def main(args: Array[String]): Unit = {
    implicit val system    : ActorSystem      = ActorSystem("SteamReviewsMicroservice")
    implicit val dispatcher: ExecutionContext = system.dispatcher
    implicit val timeout   : Timeout          = Timeout(20.seconds)

    val gameManagerActor   = system.actorOf(
      GameManagerActor.props,
      "steam-game-manager"
    )
    val userManagerActor   = system.actorOf(
      UserManagerActor.props,
      "steam-user-manager"
    )
    val reviewManagerActor = system.actorOf(
      ReviewManagerActor.props,
      "steam-review-manager"
    )
    val steamManagerWriter = system.actorOf(
      SteamManagerWriter.props(gameManagerActor, userManagerActor, reviewManagerActor),
      "steam-manager-writer"
    )
    val steamManagerReader = system.actorOf(
      SteamManagerReader.props(gameManagerActor, userManagerActor, reviewManagerActor),
      "steam-manager-reader"
    )

    val router = MainRouter(steamManagerWriter, steamManagerReader)

    val boundServer = Http().newServerAt("0.0.0.0", 8080).bind(router.routes)

    boundServer.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"Server started at: http://${address.getAddress}:${address.getPort}")

      case Failure(exception) =>
        system.log.error(s"Failed to bind server due to: $exception")
        system.terminate()
    }
  }
}
