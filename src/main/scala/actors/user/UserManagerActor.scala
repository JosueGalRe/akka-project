package dev.galre.josue.akkaProject
package actors.user

import actors.user.UserActor.UserState
import actors.{ FinishCSVLoad, InitCSVLoad, UserController }

import akka.actor.{ ActorLogging, Props }
import akka.persistence.{ PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess }
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }


object UserManagerActor {

  // users
  case class UserManager(
    var userCount: BigInt = BigInt(0),
    users:         mutable.AnyRefMap[BigInt, UserController]
  )

  // commands
  case class CreateUserFromCSV(game: UserState)

  // events
  case class UserActorCreated(id: BigInt)

  case class UserActorDeleted(id: BigInt)

  def props(implicit timeout: Timeout, executionContext: ExecutionContext): Props = Props(new UserManagerActor())
}

class UserManagerActor(implicit timeout: Timeout, executionContext: ExecutionContext)
  extends PersistentActor
  with ActorLogging {

  import UserActor._
  import UserManagerActor._

  var userManagerState: UserManager = UserManager(users = mutable.AnyRefMap())

  override def persistenceId: String = "steam-user-manager"

  def isUserAvailable(id: BigInt): Boolean =
    userManagerState.users.contains(id) && !userManagerState.users(id).isDisabled

  def createActorName(steamUserId: BigInt): String = s"steam-user-$steamUserId"

  def notFoundExceptionCreator[T](id: BigInt): Try[T] =
    Failure(NotFoundException(s"An user with the id $id couldn't be found"))


  def loadCSVData: Receive = {
    case CreateUserFromCSV(UserState(userId, name, numGamesOwned, numReviews)) =>
      if (userManagerState.users.contains(userId)) {
        log.info(s"User with Id $userId already exists, skipping creation...")
      }
      else {
        log.info(s"Creating user with id $userId")

        val userActor      = context.actorOf(
          UserActor.props(userId),
          createActorName(userId)
        )
        val controlledUser = UserController(userActor)

        persist(UserActorCreated(userId)) { _ =>
          userManagerState = userManagerState.copy(
            users = userManagerState.users.addOne(userId -> controlledUser)
          )

          userActor ! CreateUser(name.getOrElse(""), numGamesOwned, numReviews)
        }
      }

    case FinishCSVLoad =>
      context.unbecome()

    case SaveSnapshotSuccess(metadata) =>
      log.info(s"Saving snapshot succeeded: ${metadata.persistenceId} - ${metadata.timestamp}")

    case SaveSnapshotFailure(metadata, reason) =>
      log.warning(s"Saving snapshot failed: ${metadata.persistenceId} - ${metadata.timestamp} because of $reason.")

    case any: Any =>
      log.info(s"Got unhandled message: $any")
  }


  override def receiveCommand: Receive = {
    case createCommand @ CreateUser(_, _, _) =>
      val steamUserId    = userManagerState.userCount
      val userActorName  = createActorName(steamUserId)
      val userActor      = context.actorOf(
        UserActor.props(steamUserId),
        userActorName
      )
      val controlledUser = UserController(userActor)

      persist(UserActorCreated(steamUserId)) { _ =>
        userManagerState = userManagerState.copy(
          userCount = userManagerState.userCount + 1,
          users = userManagerState.users.addOne(steamUserId -> controlledUser)
        )

        userActor.forward(createCommand)
      }

    case getCommand @ GetUserInfo(id) =>
      if (isUserAvailable(id))
        userManagerState.users(id).actor.forward(getCommand)
      else
        sender() ! GetUserInfoResponse(notFoundExceptionCreator(id))

    case updateCommand @ UpdateUser(id, _, _, _) =>
      if (isUserAvailable(id))
        userManagerState.users(id).actor.forward(updateCommand)
      else
        sender() ! UserUpdatedResponse(notFoundExceptionCreator(id))

    case DeleteUser(id) =>
      if (isUserAvailable(id))
        persist(UserActorDeleted(id)) { _ =>
          userManagerState.users(id).isDisabled = true
          context.stop(userManagerState.users(id).actor)

          sender() ! UserDeletedResponse(Success(true))
        }
      else
        sender() ! UserDeletedResponse(notFoundExceptionCreator(id))

    case InitCSVLoad =>
      context.become(loadCSVData)

  }

  override def receiveRecover: Receive = {
    case UserActorCreated(steamUserId) =>
      val userActorName = createActorName(steamUserId)
      val userActor     = context.child(userActorName)
        .getOrElse(
          context.actorOf(
            UserActor.props(steamUserId),
            userActorName
          )
        )

      val controlledUser = UserController(userActor)

      userManagerState = userManagerState.copy(
        userCount = steamUserId + 1,
        userManagerState.users.addOne(steamUserId -> controlledUser)
      )

    case UserActorDeleted(id) =>
      userManagerState.users(id).isDisabled = true

  }
}
