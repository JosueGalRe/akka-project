package dev.galre.josue.steamreviews
package service

import repository.ReviewManagerActor.{ GetAllReviewsByAuthor, GetAllReviewsByGame }
import repository.entity.ReviewActor.GetReviewInfo

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }

object ReviewsReader {
  def props(reviewManagerActor: ActorRef): Props = Props(new ReviewsReader(reviewManagerActor))
}

class ReviewsReader(reviewManagerActor: ActorRef)
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
