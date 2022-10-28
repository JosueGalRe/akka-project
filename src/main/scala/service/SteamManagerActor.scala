package dev.galre.josue.akkaProject
package service

import actors.game.GameActor.GameState
import actors.review.ReviewActor.ReviewState
import actors.user.UserActor.UserState

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import cats.data.EitherT

import scala.concurrent.{ ExecutionContext, Future }

object SteamManagerActor {

  case object InitCSVLoadToManagers

  case object FinishCSVLoadToManagers

  case object Ack

  case class CSVLoadFailure(exception: Throwable)

  case class CSVDataToLoad(
    review: ReviewState,
    user:   UserState,
    game:   GameState
  )

  case class BasicUser(userId: Long, name: Option[String] = None)

  object ComposedReview {
    def apply(review: ReviewState, game: GameState, user: UserState): ComposedReview =
      ComposedReview(
        reviewId = review.reviewId,
        steamApp = game,
        author = BasicUser(user.userId, user.name),
        region = review.region,
        timestampCreated = review.timestampCreated,
        timestampUpdated = review.timestampUpdated,
        review = review.review,
        recommended = review.recommended,
        votesHelpful = review.votesHelpful,
        votesFunny = review.votesFunny,
        weightedVoteScore = review.weightedVoteScore,
        commentCount = review.commentCount,
        steamPurchase = review.steamPurchase,
        receivedForFree = review.receivedForFree,
        writtenDuringEarlyAccess = review.writtenDuringEarlyAccess,
        authorPlaytimeForever = review.authorPlaytimeForever,
        authorPlaytimeLastTwoWeeks = review.authorPlaytimeLastTwoWeeks,
        authorPlaytimeAtReview = review.authorPlaytimeAtReview,
        authorLastPlayed = review.authorLastPlayed
      )
  }

  case class ComposedReview(
    reviewId:                   Long,
    steamApp:                   GameState,
    author:                     BasicUser,
    region:                     Option[String] = None,
    review:                     Option[String] = None,
    timestampCreated:           Option[Long] = None,
    timestampUpdated:           Option[Long] = None,
    recommended:                Option[Boolean] = None,
    votesHelpful:               Option[Long] = None,
    votesFunny:                 Option[Long] = None,
    weightedVoteScore:          Option[Double] = None,
    commentCount:               Option[Long] = None,
    steamPurchase:              Option[Boolean] = None,
    receivedForFree:            Option[Boolean] = None,
    writtenDuringEarlyAccess:   Option[Boolean] = None,
    authorPlaytimeForever:      Option[Double] = None,
    authorPlaytimeLastTwoWeeks: Option[Double] = None,
    authorPlaytimeAtReview:     Option[Double] = None,
    authorLastPlayed:           Option[Double] = None,
  )

  def props(
    gameManagerActor:   ActorRef,
    userManagerActor:   ActorRef,
    reviewManagerActor: ActorRef
  )
    (implicit timeout: Timeout, executionContext: ExecutionContext): Props =
    Props(
      new SteamManagerActor(
        gameManagerActor, userManagerActor, reviewManagerActor
      )
    )
}

class SteamManagerActor(
  gameManagerActor: ActorRef, userManagerActor: ActorRef, reviewManagerActor: ActorRef
)(implicit timeout: Timeout, executionContext: ExecutionContext)
  extends Actor
  with ActorLogging {

  import actors.game.GameActor._
  import actors.game.GameManagerActor._
  import actors.review.ReviewActor._
  import actors.review.ReviewManagerActor._
  import actors.user.UserActor._
  import actors.user.UserManagerActor._

  import SteamManagerActor._

  def getComposedReview(reviewId: Long): Future[Either[String, ComposedReview]] = {
    val composedReviewFuture = for {
      review <- EitherT((reviewManagerActor ? GetReviewInfo(reviewId)).mapTo[ReviewCreatedResponse])
      game <- EitherT((gameManagerActor ? GetGameInfo(review.steamAppId)).mapTo[GetGameInfoResponse])
      user <- EitherT((userManagerActor ? GetUserInfo(review.authorId)).mapTo[GetUserInfoResponse])
    } yield {
      ComposedReview(review, game, user)
    }

    composedReviewFuture.value
  }

  override def receive: Receive = {
    // All user messages
    case createUserCommand: CreateUser =>
      userManagerActor.forward(createUserCommand)

    case getUserCommand: GetUserInfo =>
      userManagerActor.forward(getUserCommand)

    case updateUserCommand: UpdateUser =>
      userManagerActor.forward(updateUserCommand)

    case deleteUserCommand: DeleteUser =>
      userManagerActor.forward(deleteUserCommand)

    // All game messages
    case createGameCommand: CreateGame =>
      gameManagerActor.forward(createGameCommand)

    case getGameCommand: GetGameInfo =>
      gameManagerActor.forward(getGameCommand)

    case updateGameCommand: UpdateName =>
      gameManagerActor.forward(updateGameCommand)

    case deleteGameCommand: DeleteGame =>
      gameManagerActor.forward(deleteGameCommand)

    // All review messages
    case createCommand @ CreateReview(review) =>
      val steamAppId = review.steamAppId
      val authorId   = review.authorId

      val createReviewFuture = for {
        game <- EitherT((gameManagerActor ? GetGameInfo(steamAppId)).mapTo[GetGameInfoResponse])
        user <- EitherT((userManagerActor ? GetUserInfo(authorId)).mapTo[GetUserInfoResponse])
        review <- EitherT((reviewManagerActor ? createCommand).mapTo[ReviewCreatedResponse])
        _ <- EitherT((userManagerActor ? AddOneReview(authorId)).mapTo[AddedOneReviewResponse])
      } yield {
        ComposedReview(review, game, user)
      }

      createReviewFuture.value.pipeTo(sender())


    case getReviewCommand: GetReviewInfo =>
      reviewManagerActor.forward(getReviewCommand)

    case updateReviewCommand: UpdateReview =>
      reviewManagerActor.forward(updateReviewCommand)

    case deleteReviewCommand @ DeleteReview(reviewId) =>
      val deletedReviewFuture = for {
        review <- EitherT(getComposedReview(reviewId))
        reviewWasDeleted <- EitherT((gameManagerActor ? deleteReviewCommand).mapTo[ReviewDeletedResponse])
        _ <- EitherT((userManagerActor ? RemoveOneReview(review.author.userId)).mapTo[RemovedOneReviewResponse])
      } yield {
        reviewWasDeleted
      }

      deletedReviewFuture.value.pipeTo(sender())

    case getAllReviewsByUserCommand: GetAllReviewsByAuthor =>
      reviewManagerActor.forward(getAllReviewsByUserCommand)

    case getAllReviewsByGameCommand: GetAllReviewsByGame =>
      reviewManagerActor.forward(getAllReviewsByGameCommand)

    // All CSVLoad messages
    case InitCSVLoadToManagers =>
      log.info("Initialized CSV Data load.")
      sender() ! Ack

    case CSVDataToLoad(review, user, game) =>
      log.info(s"Received CSV Data for review ${review.reviewId}")
      gameManagerActor ! CreateGameFromCSV(game)
      userManagerActor ! CreateUserFromCSV(user)
      reviewManagerActor ! CreateReviewFromCSV(review)
      sender() ! Ack

    case FinishCSVLoadToManagers =>
      log.info("Finished successfully CSV Load")

    case CSVLoadFailure(exception) =>
      log.error(
        s"CSV Load failed due to ${exception.getMessage}.\nStack trace: ${
          exception
            .printStackTrace()
        }\nexception: ${exception.toString}"
      )
  }
}
