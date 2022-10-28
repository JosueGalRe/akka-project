package dev.galre.josue.akkaProject
package controller

import actors.review.ReviewManagerActor.{ GetAllReviewsByAuthor, GetAllReviewsByFilterResponse, GetAllReviewsByGame }
import service.SteamManagerActor.ComposedReview

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ Directives, Route }
import akka.pattern.ask
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.{ ExecutionContext, Future }

case class ReviewRouter(
  steamManagerActor: ActorRef
)
  (implicit timeout: Timeout, executionContext: ExecutionContext) extends Directives {

  import actors.review.ReviewActor._

  private case class CreateReviewRequest(
    steamAppId:                 Long,
    authorId:                   Long,
    region:                     String,
    review:                     String,
    recommended:                Boolean,
    commentCount:               Option[Long],
    votesFunny:                 Option[Long],
    votesHelpful:               Option[Long],
    steamPurchase:              Boolean,
    receivedForFree:            Option[Boolean],
    writtenDuringEarlyAccess:   Boolean,
    authorPlaytimeForever:      Option[Double],
    authorPlaytimeLastTwoWeeks: Option[Double],
    authorPlaytimeAtReview:     Option[Double],
    authorLastPlayed:           Option[Double]
  ) {
    def toCommand: CreateReview = {
      val timestampCreated            = Option(System.currentTimeMillis())
      val timestampUpdated            = timestampCreated
      val weightedVoteScore           = Option(0D)
      val newRegion                   = Option(region)
      val newReview                   = Option(review)
      val newRecommended              = Option(recommended)
      val newSteamPurchase            = Option(steamPurchase)
      val newWrittenDuringEarlyAccess = Option(writtenDuringEarlyAccess)

      val reviewState = ReviewState(
        reviewId = 0,
        steamAppId = steamAppId,
        authorId = authorId,
        region = newRegion,
        timestampCreated = timestampCreated,
        timestampUpdated = timestampUpdated,
        review = newReview,
        recommended = newRecommended,
        votesHelpful = votesHelpful,
        votesFunny = votesFunny,
        weightedVoteScore = weightedVoteScore,
        commentCount = commentCount,
        steamPurchase = newSteamPurchase,
        receivedForFree = receivedForFree,
        writtenDuringEarlyAccess = newWrittenDuringEarlyAccess,
        authorPlaytimeForever = authorPlaytimeForever,
        authorPlaytimeLastTwoWeeks = authorPlaytimeLastTwoWeeks,
        authorPlaytimeAtReview = authorPlaytimeAtReview,
        authorLastPlayed = authorLastPlayed
      )

      CreateReview(reviewState)
    }
  }

  private case class UpdateReviewRequest(
    region:                     Option[String],
    review:                     Option[String],
    recommended:                Option[Boolean],
    votesHelpful:               Option[Long],
    votesFunny:                 Option[Long],
    commentCount:               Option[Long],
    receivedForFree:            Option[Boolean],
    authorPlaytimeForever:      Option[Double],
    authorPlaytimeLastTwoWeeks: Option[Double],
    authorPlaytimeAtReview:     Option[Double],
    authorLastPlayed:           Option[Double]
  ) {
    def toCommand(id: Long): UpdateReview = {
      val weightedVoteScore = Option(0D)

      UpdateReview(
        ReviewState(
          reviewId = id,
          region = region,
          review = review,
          recommended = recommended,
          votesHelpful = votesHelpful,
          votesFunny = votesFunny,
          weightedVoteScore = weightedVoteScore,
          commentCount = commentCount,
          receivedForFree = receivedForFree,
          authorPlaytimeForever = authorPlaytimeForever,
          authorPlaytimeLastTwoWeeks = authorPlaytimeLastTwoWeeks,
          authorPlaytimeAtReview = authorPlaytimeAtReview,
          authorLastPlayed = authorLastPlayed
        )
      )
    }
  }

  private def createReviewAction(createReview: CreateReviewRequest): Future[Either[String, ComposedReview]] =
    (steamManagerActor ? createReview.toCommand).mapTo[Either[String, ComposedReview]]

  private def updateNameAction(id: Long, updateReview: UpdateReviewRequest): Future[ReviewUpdatedResponse] =
    (steamManagerActor ? updateReview.toCommand(id)).mapTo[ReviewUpdatedResponse]

  private def getReviewInfoAction(id: Long): Future[GetReviewInfoResponse] =
    (steamManagerActor ? GetReviewInfo(id)).mapTo[GetReviewInfoResponse]

  private def deleteReviewAction(id: Long): Future[ReviewDeletedResponse] =
    (steamManagerActor ? DeleteReview(id)).mapTo[ReviewDeletedResponse]

  private def getAllReviewsByUser(id: Long, page: Int, perPage: Int): Future[GetAllReviewsByFilterResponse] =
    (steamManagerActor ? GetAllReviewsByAuthor(id, page, perPage)).mapTo[GetAllReviewsByFilterResponse]

  private def getAllReviewsByGame(id: Long, page: Int, perPage: Int): Future[GetAllReviewsByFilterResponse] =
    (steamManagerActor ? GetAllReviewsByGame(id, page, perPage)).mapTo[GetAllReviewsByFilterResponse]

  val routes: Route =
    pathPrefix("reviews") {
      concat(
        pathPrefix("filter") {
          get {
            concat(
              path("user" / LongNumber) { authorId =>
                paginationParameters { (page, perPage) =>
                  onSuccess(getAllReviewsByUser(authorId, page, perPage)) {
                    case Right(allReviews) =>
                      complete(StatusCodes.OK, allReviews)

                    case Left(failure) =>
                      completeWithMessage(StatusCodes.BadRequest, Some(failure))
                  }
                }
              },
              path("game" / LongNumber) { steamAppId =>
                paginationParameters { (page, perPage) =>
                  onSuccess(getAllReviewsByGame(steamAppId, page, perPage)) {
                    case Right(allReviews) =>
                      complete(allReviews)

                    case Left(exception) =>
                      completeWithMessage(StatusCodes.BadRequest, Some(exception))
                  }

                }
              }

            )
          }
        },
        path(LongNumber) { steamReviewId =>
          concat(
            get {
              onSuccess(getReviewInfoAction(steamReviewId)) {
                case Right(state) =>
                  complete(state)

                case Left(exception) =>
                  completeWithMessage(StatusCodes.BadRequest, Some(exception))
              }
            },
            patch {
              entity(as[UpdateReviewRequest]) { updateName =>
                onSuccess(updateNameAction(steamReviewId, updateName)) {
                  case Right(state) =>
                    complete(state)

                  case Left(exception) =>
                    completeWithMessage(StatusCodes.BadRequest, Some(exception))
                }
              }
            },
            delete {
              onSuccess(deleteReviewAction(steamReviewId)) {
                case Right(_) =>
                  completeWithMessage(StatusCodes.OK, Some("Review was deleted successfully."))

                case Left(exception) =>
                  completeWithMessage(StatusCodes.BadRequest, Some(exception))
              }

            }
          )
        },
        pathEndOrSingleSlash {
          post {
            entity(as[CreateReviewRequest]) { review =>
              onSuccess(createReviewAction(review)) {
                case Right(composedReview) =>
                  complete(composedReview)

                case Left(exception) =>
                  completeWithMessage(StatusCodes.BadRequest, Some(exception))
              }

            }
          }
        },
      )
    }
}
