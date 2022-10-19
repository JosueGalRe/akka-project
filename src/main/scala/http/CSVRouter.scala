package dev.galre.josue.akkaProject
package http

import actors.data.CSVLoaderActor

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

case class CSVRouter(
  steamManagerActor: ActorRef
)(
  implicit system: ActorSystem, timeout: Timeout, executionContext: ExecutionContext
) {

  private      val csvFile        = "src/main/resources/steam_reviews.csv"
  private lazy val csvLoaderActor = system.actorOf(
    CSVLoaderActor.props(steamManagerActor),
    "json-loader"
  )

  private def startCSVLoadAction(quantity: Int): Future[String] =
    (csvLoaderActor ? CSVLoaderActor.LoadCSV(csvFile, numberOfElements = quantity)).mapTo[String]

  val routes: Route =
    pathPrefix("csv") {
      path("load") {
        get {
          parameter(Symbol("quantity").withDefault(Int.MaxValue)) { quantity =>
            onComplete(startCSVLoadAction(quantity)) {
              case Failure(exception) =>
                completeWithMessage(StatusCodes.BadRequest, Some(s"Failed to load CSV due to: ${exception.getMessage}"))

              case Success(message) =>
                completeWithMessage(StatusCodes.OK, Some(message))
            }
          }
        }
      }
    }

}
