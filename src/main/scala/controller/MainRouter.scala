package dev.galre.josue.akkaProject
package controller

import service.utils.swagger.SwaggerDocService

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import scala.concurrent.ExecutionContext

case class MainRouter(
  steamManagerActor: ActorRef
)(
  implicit system: ActorSystem, timeout: Timeout, executionContext: ExecutionContext
) {

  val routes: Route =
    pathPrefix("api") {
      concat(
        GameRouter(steamManagerActor).routes,
        UserRouter(steamManagerActor).routes,
        ReviewRouter(steamManagerActor).routes,
        CSVRouter(steamManagerActor).routes,
        SwaggerDocService.routes
      )
    }

}
