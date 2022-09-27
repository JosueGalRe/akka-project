package dev.galre.josue.akkaProject
package http

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.{ Directives, Route }
import akka.pattern.ask
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.util.{ Failure, Success }

case class UserRouter(gameManagerActor: ActorRef)(implicit timeout: Timeout) extends Directives {

  import actors.UserActor._

  private case class CreateUserRequest(name: String, numGamesOwned: Option[Int], numReviews: Option[Int]) {
    def toCommand: CreateUser = {
      val newNumGamesOwned = if (numGamesOwned.isEmpty) Some(0) else numGamesOwned
      val newNumReviews    = if (numReviews.isEmpty) Some(0) else numReviews

      CreateUser(name, newNumGamesOwned, newNumReviews)
    }
  }

  private case class UpdateUserRequest(name: Option[String], numGamesOwned: Option[Int], numReviews: Option[Int]) {
    def toCommand(id: BigInt): UpdateUser = UpdateUser(id, name, numGamesOwned, numReviews)
  }

  private def createUserAction(createUser: CreateUserRequest): Future[UserCreatedResponse] =
    (gameManagerActor ? createUser.toCommand).mapTo[UserCreatedResponse]

  private def updateNameAction(id: BigInt, updateUser: UpdateUserRequest): Future[UserUpdatedResponse] =
    (gameManagerActor ? updateUser.toCommand(id)).mapTo[UserUpdatedResponse]

  private def getUserInfoAction(id: BigInt): Future[GetUserInfoResponse] =
    (gameManagerActor ? GetUserInfo(id)).mapTo[GetUserInfoResponse]

  private def deleteUserAction(id: BigInt): Future[UserDeletedResponse] =
    (gameManagerActor ? DeleteUser(id)).mapTo[UserDeletedResponse]


  val routes: Route =
    pathPrefix("users") {
      concat(
        pathEndOrSingleSlash {
          post {
            entity(as[CreateUserRequest]) { game =>
              onSuccess(createUserAction(game)) {
                case UserCreatedResponse(Success(steamUserId)) =>
                  respondWithHeader(Location(s"/users/$steamUserId")) {
                    complete(StatusCodes.Created)
                  }

                case UserCreatedResponse(Failure(exception)) =>
                  throw exception
              }
            }
          }
        },
        path(LongNumber) { steamUserId =>
          concat(
            get {
              onSuccess(getUserInfoAction(steamUserId)) {
                case GetUserInfoResponse(Success(state)) =>
                  complete(state)

                case GetUserInfoResponse(Failure(exception)) =>
                  throw exception
              }
            },
            patch {
              entity(as[UpdateUserRequest]) { updateName =>
                onSuccess(updateNameAction(steamUserId, updateName)) {
                  case UserUpdatedResponse(Success(state)) =>
                    complete(state)

                  case UserUpdatedResponse(Failure(exception)) =>
                    throw exception
                }
              }
            },
            delete {
              onSuccess(deleteUserAction(steamUserId)) {
                case UserDeletedResponse(Success(_)) =>
                  complete(Response(statusCode = StatusCodes.OK.intValue, message = Some("User was deleted successfully.")))

                case UserDeletedResponse(Failure(exception)) =>
                  throw exception
              }
            }
          )
        }
      )
    }
}