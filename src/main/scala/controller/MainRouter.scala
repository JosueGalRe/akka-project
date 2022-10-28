package dev.galre.josue.akkaProject
package controller

import service.utils.swagger.SwaggerDocService

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import scala.concurrent.ExecutionContext

case class MainRouter(
  steamManagerWriter: ActorRef, steamManagerReader: ActorRef
)(
  implicit system: ActorSystem, timeout: Timeout, executionContext: ExecutionContext
) {

  val routes: Route =
    pathPrefix("api") {
      concat(
        GameRouter(steamManagerWriter, steamManagerReader).routes,
        UserRouter(steamManagerWriter, steamManagerReader).routes,
        ReviewRouter(steamManagerWriter, steamManagerReader).routes,
        CSVRouter(steamManagerWriter).routes,
        SwaggerDocService.routes
      )
    }

}
