package dev.galre.josue.steamreviews
package controller

import Application.StateManagers
import service.utils.swagger.SwaggerDocService

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout

final case class MainRouter(
  stateManagers: StateManagers
)(implicit timeout: Timeout) {

  val routes: Route =
    pathPrefix("api") {
      concat(
        GameRouter(stateManagers.gamesWriter, stateManagers.gamesReader).routes,
        UserRouter(stateManagers.usersWriter, stateManagers.usersReader).routes,
        ReviewRouter(stateManagers.reviewsWriter, stateManagers.reviewsReader).routes,
        CSVRouter(stateManagers.csvLoader).routes,
        SwaggerDocService.routes
      )
    }

}
