package dev.galre.josue.steamreviews
package service.utils

import repository.GameManagerActor.CreateGameFromCSV
import repository.ReviewManagerActor.CreateReviewFromCSV
import repository.UserManagerActor.CreateUserFromCSV
import repository.entity.GameActor.GameState
import repository.entity.ReviewActor.ReviewState
import repository.entity.UserActor.UserState

import CSVLoaderActor._
import akka.NotUsed
import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import akka.stream.alpakka.csv.scaladsl.{ CsvParsing, CsvToMap }
import akka.stream.scaladsl.{ FileIO, Flow, Sink }
import akka.util.ByteString

import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import scala.concurrent.duration._

object CSVLoaderActor {

  case object InitCSVLoadToManagers

  case object FinishCSVLoadToManagers

  case object Ack

  final case class CSVLoadFailure(exception: Throwable)

  final case class CSVRow(
    reviewId: Long,
    steamAppId: Long,
    authorId: Long,
    region: Option[String],
    reviewValue: Option[String],
    timestampCreated: Option[Long],
    timestampUpdated: Option[Long],
    recommended: Option[Boolean],
    votesHelpful: Option[Long],
    votesFunny: Option[Long],
    weightedVoteScore: Option[Double],
    commentCount: Option[Long],
    steamPurchase: Option[Boolean],
    receivedForFree: Option[Boolean],
    writtenDuringEarlyAccess: Option[Boolean],
    playtimeForever: Option[Double],
    playtimeLastTwoWeeks: Option[Double],
    playtimeAtReview: Option[Double],
    lastPlayed: Option[Double],
    name: Option[String],
    numGamesOwned: Option[Int],
    numReviews: Option[Int],
    steamAppName: String,
    csvEntry: Long,
  )

  final case class CSVDataToLoad(
    csvEntryId: Long,
    review: ReviewState,
    user: UserState,
    game: GameState
  )

  // commands
  final case class LoadCSV(file: String, startPosition: Long = 0, numberOfElements: Int)

  def props(
    gameWriter: ActorRef,
    reviewWriter: ActorRef,
    userWriter: ActorRef
  )
    (implicit system: ActorSystem): Props =
    Props(new CSVLoaderActor(gameWriter, reviewWriter, userWriter))
}

class CSVLoaderActor(
  gameWriter: ActorRef,
  reviewWriter: ActorRef,
  userWriter: ActorRef
)
  (implicit system: ActorSystem)
  extends Actor
  with ActorLogging {

  def extractAndConvertRow(row: Map[String, String]): CSVRow = {
    val reviewId = row("review_id").toLong
    val steamAppId = row("app_id").toLong
    val authorId = row("author.steamid").toLong
    val region = row.get("language")
    val reviewValue = row.get("review")
    val timestampCreated = row.get("timestamp_created").flatMap(_.toLongOption)
    val timestampUpdated = row.get("timestamp_updated").flatMap(_.toLongOption)
    val recommended = row("recommended").toBooleanOption
    val votesHelpful = row("votes_helpful").toLongOption
    val votesFunny = row("votes_funny").toLongOption
    val weightedVoteScore = row("weighted_vote_score").toDoubleOption
    val commentCount = row("comment_count").toLongOption
    val steamPurchase = row("steam_purchase").toBooleanOption
    val receivedForFree = row("received_for_free").toBooleanOption
    val writtenDuringEarlyAccess = row("written_during_early_access").toBooleanOption
    val playtimeForever = row("author.playtime_forever").toDoubleOption
    val playtimeLastTwoWeeks = row("author.playtime_last_two_weeks").toDoubleOption
    val playtimeAtReview = row("author.playtime_at_review").toDoubleOption
    val lastPlayed = row.get("author.last_played").flatMap(value => value.toDoubleOption)

    val name = Some(s"user$authorId")
    val numGamesOwned = row("author.num_games_owned").toIntOption
    val numReviews = row("author.num_reviews").toIntOption

    val steamAppName = row("app_name")

    val csvEntry = row("").toLong

    CSVRow(
      reviewId,
      steamAppId,
      authorId,
      region,
      reviewValue,
      timestampCreated,
      timestampUpdated,
      recommended,
      votesHelpful,
      votesFunny,
      weightedVoteScore,
      commentCount,
      steamPurchase,
      receivedForFree,
      writtenDuringEarlyAccess,
      playtimeForever,
      playtimeLastTwoWeeks,
      playtimeAtReview,
      lastPlayed,
      name,
      numGamesOwned,
      numReviews,
      steamAppName,
      csvEntry
    )
  }

  def convertCSVData(row: CSVRow): CSVDataToLoad = {
    val review = ReviewState(
      row.reviewId,
      row.steamAppId,
      row.authorId,
      row.region,
      row.reviewValue,
      row.timestampCreated,
      row.timestampUpdated,
      row.recommended,
      row.votesHelpful,
      row.votesFunny,
      row.weightedVoteScore,
      row.commentCount,
      row.steamPurchase,
      row.receivedForFree,
      row.writtenDuringEarlyAccess,
      row.playtimeForever,
      row.playtimeLastTwoWeeks,
      row.playtimeAtReview,
      row.lastPlayed
    )

    val user = UserState(
      row.authorId,
      row.name,
      row.numGamesOwned,
      row.numReviews
    )

    val game = GameState(
      row.steamAppId,
      row.steamAppName
    )

    CSVDataToLoad(
      row.csvEntry,
      review,
      user,
      game
    )
  }

  val csvParserFlowFromZero: Flow[List[ByteString], CSVDataToLoad, NotUsed] = {
    CsvToMap
      .toMapAsStrings(StandardCharsets.UTF_8)
      .map(extractAndConvertRow)
      .map(convertCSVData)
  }

  val csvParserFlowFromPosition: Flow[List[ByteString], CSVDataToLoad, NotUsed] = {
    CsvToMap
      .withHeadersAsStrings(
        StandardCharsets.UTF_8,
        "",
        "app_id",
        "app_name",
        "review_id",
        "language",
        "review",
        "timestamp_created",
        "timestamp_updated",
        "recommended",
        "votes_helpful",
        "votes_funny",
        "weighted_vote_score",
        "comment_count",
        "steam_purchase",
        "received_for_free",
        "written_during_early_access",
        "author.steamid",
        "author.num_games_owned",
        "author.num_reviews",
        "author.playtime_forever",
        "author.playtime_last_two_weeks",
        "author.playtime_at_review",
        "author.last_played"
      )
      .map(extractAndConvertRow)
      .map(convertCSVData)
  }

  def csvTransformerToDataEntities(startPosition: Long): Flow[List[ByteString], CSVDataToLoad, NotUsed] =
    if (startPosition == 0) {
      csvParserFlowFromZero
    } else {
      csvParserFlowFromPosition
    }

  private val chunkSize = 8192
  private val elements = 1000

  override def receive: Receive = {
    case LoadCSV(file, startPosition, numberOfElements) =>
      log.info(s"reading file $file")

      FileIO.fromPath(Paths.get(file), chunkSize, startPosition)
        .via(CsvParsing.lineScanner(maximumLineLength = Int.MaxValue))
        .via(csvTransformerToDataEntities(startPosition))
        .throttle(elements, 3.seconds)
        .take(numberOfElements)
        .runWith(
          Sink.actorRefWithBackpressure(
            ref = self,
            onInitMessage = InitCSVLoadToManagers,
            onCompleteMessage = FinishCSVLoadToManagers,
            onFailureMessage = CSVLoadFailure
          )
        )

      sender() !
        s"Initialized CSV load of file $file with $numberOfElements elements at position $startPosition."

    // All CSVLoad messages
    case InitCSVLoadToManagers =>
      log.info("Initialized CSV Data load.")
      sender() ! Ack

    case CSVDataToLoad(csvEntryId, review, user, game) =>
      log.info(s"Received CSV Data for review ${review.reviewId} ($csvEntryId)")

      gameWriter ! CreateGameFromCSV(game)
      userWriter ! CreateUserFromCSV(user)
      reviewWriter ! CreateReviewFromCSV(review)

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
