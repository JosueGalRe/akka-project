package dev.galre.josue.steamreviews
package service

import repository.GameManagerActor.CreateGameFromCSV
import repository.entity.GameActor._

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }

object GamesWriter {

  def props(gameManagerActor: ActorRef): Props =
    Props(
      new GamesWriter(
        gameManagerActor
      )
    )
}

class GamesWriter(gameManagerActor: ActorRef)
  extends Actor
  with ActorLogging {

  override def receive: Receive = {
    // All game messages
    case createGameCommand: CreateGame =>
      gameManagerActor.forward(createGameCommand)

    case updateGameCommand: UpdateName =>
      gameManagerActor.forward(updateGameCommand)

    case deleteGameCommand: DeleteGame =>
      gameManagerActor.forward(deleteGameCommand)

    case createCSVCommand: CreateGameFromCSV =>
      gameManagerActor.forward(createCSVCommand)

  }
}
