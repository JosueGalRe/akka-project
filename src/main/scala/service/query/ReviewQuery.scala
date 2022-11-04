package dev.galre.josue.steamreviews
package service.query

import repository.ReviewManagerActor.{ GetAllReviewsByAuthor, GetAllReviewsByGame }
import repository.entity.ReviewActor.GetReviewInfo

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }

object ReviewQuery {
  def props(reviewManagerActor: ActorRef): Props = Props(new ReviewQuery(reviewManagerActor))
}

class ReviewQuery(reviewManagerActor: ActorRef)
  extends Actor
  with ActorLogging {

  override def receive: Receive = {

    case getReviewCommand: GetReviewInfo =>
      reviewManagerActor.forward(getReviewCommand)

    case getAllReviewsByUserCommand: GetAllReviewsByAuthor =>
      reviewManagerActor.forward(getAllReviewsByUserCommand)

    case getAllReviewsByGameCommand: GetAllReviewsByGame =>
      reviewManagerActor.forward(getAllReviewsByGameCommand)
  }
}
