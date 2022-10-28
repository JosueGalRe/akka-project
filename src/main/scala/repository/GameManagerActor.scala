package dev.galre.josue.akkaProject
package repository

import repository.entity.GameActor
import repository.entity.GameActor.GameState
import service.utils.Serializable

import akka.actor.{ ActorLogging, Props }
import akka.pattern.{ ask, pipe }
import akka.persistence._
import akka.util.Timeout
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.Success

object GameManagerActor {

  // games
  case class GameManager(
    var gameCount: Long = 0,
    games:         mutable.HashMap[Long, GameController]
  ) extends Serializable

  val gameManagerSnapshotInterval = 10

  // commands
  case class CreateGameFromCSV(game: GameState)

  // events
  case class GameActorCreated(
    @JsonDeserialize(contentAs = classOf[Long]) id: Long,
    steamAppName:                                   String
  ) extends Serializable

  case class GameActorUpdated(
    @JsonDeserialize(contentAs = classOf[Long]) id: Long,
    steamAppName:                                   String
  ) extends Serializable

  case class GameActorDeleted(id: Long) extends Serializable

  // snapshot

  case class GameSave(
    steamAppId:   Long,
    steamAppName: String
  )

  case class GameManagerSnapshotSave(
    gameCount:     Long,
    gameTupleList: List[GameSave]
  )

  def props(implicit timeout: Timeout, executionContext: ExecutionContext): Props = Props(new GameManagerActor())
}

class GameManagerActor(implicit timeout: Timeout, executionContext: ExecutionContext)
  extends PersistentActor
  with ActorLogging {

  import GameActor._
  import GameManagerActor._

  var gameManagerState: GameManager = GameManager(games = mutable.HashMap())

  override def persistenceId: String = "steam-games-manager"

  def createActorName(steamGameId: Long): String = s"steam-app-$steamGameId"

  def gameAlreadyExists(steamAppName: String): Boolean =
    gameManagerState.games.values.exists(game => game.name == steamAppName && !game.isDisabled)

  def isGameAvailable(id: Long): Boolean =
    gameManagerState.games.contains(id) && !gameManagerState.games(id).isDisabled

  def notFoundExceptionCreator[T](id: Long): Either[String, T] =
    Left(s"A game with the id $id couldn't be found")

  // TODO: Fix snapshotting
  def tryToSaveSnapshot(): Unit =
    if (lastSequenceNr % gameManagerSnapshotInterval == 0 && lastSequenceNr != 0) {
      val snapshotQuantity = lastSequenceNr / gameManagerSnapshotInterval
      val gamesToDrop      = (
        (snapshotQuantity * gameManagerSnapshotInterval) - gameManagerSnapshotInterval
        ).toInt
      val gamesList        = gameManagerState.games.drop(gamesToDrop).map(game => GameSave(game._1, game._2.name)).toList
      val snapshotToSave   = GameManagerSnapshotSave(gameManagerState.gameCount, gamesList)

      saveSnapshot(snapshotToSave)
    }


  override def receiveCommand: Receive = {
    case createCommand @ CreateGame(steamAppName) =>
      if (gameAlreadyExists(steamAppName))
        sender() ! Left("A game with this name already exists.")
      else {
        val steamGameId    = gameManagerState.gameCount
        val gameActorName  = createActorName(steamGameId)
        val gameActor      = context.actorOf(
          GameActor.props(steamGameId),
          gameActorName
        )
        val controlledGame = GameController(gameActor, steamAppName)

        persist(GameActorCreated(steamGameId, steamAppName)) { _ =>
          gameManagerState = gameManagerState.copy(
            gameCount = gameManagerState.gameCount + 1,
            games = gameManagerState.games.addOne(steamGameId -> controlledGame)
          )

          tryToSaveSnapshot()

          gameActor.forward(createCommand)
        }
      }

    case getCommand @ GetGameInfo(id) =>
      if (isGameAvailable(id))
        gameManagerState.games(id).actor.forward(getCommand)
      else
        sender() ! notFoundExceptionCreator(id)

    case updateCommand @ UpdateName(id, newName) =>
      if (isGameAvailable(id))
        (gameManagerState.games(id).actor ? updateCommand).mapTo[GameUpdatedResponse].pipeTo(sender()).andThen {
          case Success(gameUpdatedResponse) => gameUpdatedResponse match {
            case Right(_) =>
              persist(GameActorUpdated(id, newName)) { _ =>
                gameManagerState.games(id).name = newName

                tryToSaveSnapshot()
              }

            case _ =>
          }

          case _ =>
        }
      else
        sender() ! notFoundExceptionCreator(id)

    case DeleteGame(id) =>
      if (isGameAvailable(id))
        persist(GameActorDeleted(id)) { _ =>
          gameManagerState.games(id).isDisabled = true
          context.stop(gameManagerState.games(id).actor)

          tryToSaveSnapshot()

          sender() ! Right(true)
        }
      else
        sender() ! notFoundExceptionCreator(id)

    case CreateGameFromCSV(GameState(steamAppId, steamAppName)) =>
      if (!gameManagerState.games.contains(steamAppId)) {
        val gameActor      = context.actorOf(
          GameActor.props(steamAppId),
          createActorName(steamAppId)
        )
        val controlledGame = GameController(gameActor, steamAppName)

        persist(GameActorCreated(steamAppId, steamAppName)) { _ =>
          gameManagerState = gameManagerState.copy(
            games = gameManagerState.games.addOne(steamAppId -> controlledGame)
          )

          gameActor ! CreateGame(steamAppName)
        }
      }

    case SaveSnapshotSuccess(metadata) =>
      log.info(s"Saving snapshot succeeded: ${metadata.persistenceId} - ${metadata.timestamp}")

    case SaveSnapshotFailure(metadata, reason) =>
      log.warning(s"Saving snapshot failed: ${metadata.persistenceId} - ${metadata.timestamp} because of $reason.")

    case any: Any =>
      log.info(s"Got unhandled message: $any")

  }

  def createGameFromRecover(steamGameId: Long, steamAppName: String): GameController = {
    val gameActorName = createActorName(steamGameId)
    val gameActor     = context.child(gameActorName)
      .getOrElse(
        context.actorOf(
          GameActor.props(steamGameId),
          gameActorName
        )
      )

    GameController(gameActor, steamAppName)
  }

  override def receiveRecover: Receive = {
    case GameActorCreated(steamGameId, steamAppName) =>
      val controlledGame = createGameFromRecover(steamGameId, steamAppName)

      gameManagerState = gameManagerState.copy(
        gameCount = steamGameId + 1,
        gameManagerState.games.addOne(steamGameId -> controlledGame)
      )

    case GameActorDeleted(id) =>
      gameManagerState.games(id).isDisabled = true

    case GameActorUpdated(id, name) =>
      gameManagerState.games(id).name = name

    case SnapshotOffer(metadata, GameManagerSnapshotSave(gameCount, gameTupleList)) =>
      log.info(s"Recovered game snapshot ${metadata.persistenceId} - ${metadata.timestamp}")
      gameManagerState = gameManagerState.copy(gameCount = gameCount)

      gameTupleList.foreach {
        case GameSave(steamAppId, steamAppName) =>
          val controlledGame = createGameFromRecover(steamAppId, steamAppName)

          gameManagerState = gameManagerState.copy(
            games = gameManagerState.games.addOne(steamAppId -> controlledGame)
          )
      }

    case RecoveryCompleted =>
      log.info("Recovery completed successfully.")
  }

}
