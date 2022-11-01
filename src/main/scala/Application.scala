package dev.galre.josue.steamreviews

import controller.MainRouter
import repository.{ GameManagerActor, ReviewManagerActor, UserManagerActor }
import service._

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object Application {
  final case class StateManagers(
    gamesWriter: ActorRef,
    gamesReader: ActorRef,
    reviewsWriter: ActorRef,
    reviewsReader: ActorRef,
    usersWriter: ActorRef,
    usersReader: ActorRef,
    csvLoader: ActorRef,
  )

  // TODO: Implement CQRS (Sebastian suggested to wait until module 9 to implement this, along with other techniques)
  // TODO: Move actor creation to separated file in utils
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("SteamReviewsMicroservice")
    implicit val dispatcher: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout = Timeout(20.seconds)

    val gameManagerActor = system.actorOf(
      GameManagerActor.props,
      "steam-game-manager"
    )
    val userManagerActor = system.actorOf(
      UserManagerActor.props,
      "steam-user-manager"
    )
    val reviewManagerActor = system.actorOf(
      ReviewManagerActor.props,
      "steam-review-manager"
    )

    val gamesWriter = system.actorOf(
      GamesWriter.props(gameManagerActor),
      "games-writer"
    )
    val gamesReader = system.actorOf(
      GamesReader.props(gameManagerActor),
      "games-reader"
    )

    val reviewsWriter = system.actorOf(
      ReviewsWriter.props(gameManagerActor, userManagerActor, reviewManagerActor),
      "reviews-writer"
    )
    val reviewsReader = system.actorOf(
      ReviewsReader.props(reviewManagerActor),
      "reviews-reader"
    )

    val usersReader = system.actorOf(
      UsersReader.props(userManagerActor),
      "users-writer"
    )
    val usersWriter = system.actorOf(
      UsersWriter.props(userManagerActor),
      "users-reader"
    )

    val csvLoaderActor = system.actorOf(
      utils.CSVLoaderActor.props(gamesWriter, reviewsWriter, usersWriter),
      "json-loader"
    )

    val stateManagers = StateManagers(
      gamesWriter,
      gamesReader,
      reviewsWriter,
      reviewsReader,
      usersWriter,
      usersReader,
      csvLoaderActor
    )

    val router = MainRouter(stateManagers)

    val address = "0.0.0.0"
    val port = 8080
    val boundServer = Http().newServerAt(address, port).bind(router.routes)

    boundServer.onComplete {
      case Success(binding) =>
        val boundAddress = binding.localAddress
        val boundLocation = boundAddress.getAddress
        val boundPort = boundAddress.getPort

        system.log.info(s"Server started at: http://$boundLocation:$boundPort")

      case Failure(exception) =>
        system.log.error(s"Failed to bind server due to: $exception")
        system.terminate()
    }
  }
}
