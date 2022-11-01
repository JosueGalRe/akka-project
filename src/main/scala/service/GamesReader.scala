package dev.galre.josue.steamreviews
package service

import repository.entity.GameActor.GetGameInfo

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }

object GamesReader {
  def props(gameManagerActor: ActorRef): Props
  = Props(new GamesReader(gameManagerActor))
}

class GamesReader(gameManagerActor: ActorRef)
  extends Actor
  with ActorLogging {

  override def receive: Receive = {

    case getGameCommand: GetGameInfo =>
      gameManagerActor.forward(getGameCommand)
  }
}
