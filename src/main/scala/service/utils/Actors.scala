package dev.galre.josue.steamreviews
package service.utils

import repository.{ GameManagerActor, ReviewManagerActor, UserManagerActor }
import service._

import akka.actor.{ ActorRef, ActorSystem }
import akka.util.Timeout

import scala.concurrent.ExecutionContext

object Actors {

  final case class StateManagers(
    gamesWriter: ActorRef,
    gamesReader: ActorRef,
    reviewsWriter: ActorRef,
    reviewsReader: ActorRef,
    usersWriter: ActorRef,
    usersReader: ActorRef,
    csvLoader: ActorRef,
  )

  def init(
    implicit system: ActorSystem,
    dispatcher: ExecutionContext,
    timeout: Timeout
  ): StateManagers = {

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

    StateManagers(
      gamesWriter,
      gamesReader,
      reviewsWriter,
      reviewsReader,
      usersWriter,
      usersReader,
      csvLoaderActor
    )
  }
}
