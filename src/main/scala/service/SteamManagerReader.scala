package dev.galre.josue.akkaProject
package service

import repository.ReviewManagerActor.{ GetAllReviewsByAuthor, GetAllReviewsByGame }
import repository.entity.GameActor.GetGameInfo
import repository.entity.ReviewActor.GetReviewInfo
import repository.entity.UserActor.GetUserInfo

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import akka.util.Timeout

import scala.concurrent.ExecutionContext

object SteamManagerReader {
  def props(
    gameManagerActor: ActorRef, userManagerActor: ActorRef, reviewManagerActor: ActorRef
  )(implicit system: ActorSystem, timeout: Timeout, executionContext: ExecutionContext): Props
  = Props(new SteamManagerReader(gameManagerActor, userManagerActor, reviewManagerActor))
}

class SteamManagerReader(
  gameManagerActor: ActorRef, userManagerActor: ActorRef, reviewManagerActor: ActorRef
)(implicit system: ActorSystem, timeout: Timeout, executionContext: ExecutionContext)
  extends Actor
  with ActorLogging {

  override def receive: Receive = {

    case getUserCommand: GetUserInfo =>
      userManagerActor.forward(getUserCommand)

    case getGameCommand: GetGameInfo =>
      gameManagerActor.forward(getGameCommand)

    case getReviewCommand: GetReviewInfo =>
      reviewManagerActor.forward(getReviewCommand)

    case getAllReviewsByUserCommand: GetAllReviewsByAuthor =>
      reviewManagerActor.forward(getAllReviewsByUserCommand)

    case getAllReviewsByGameCommand: GetAllReviewsByGame =>
      reviewManagerActor.forward(getAllReviewsByGameCommand)
  }
}
