package dev.galre.josue.steamreviews

import controller.MainRouter
import service.utils.Actors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object Application {
  // TODO: Implement CQRS
  // (Sebastian suggested to wait until module 9 to implement this, along with other techniques)
  // TODO: Move actor creation to separated file in utils
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("SteamReviewsMicroservice")
    implicit val dispatcher: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout = Timeout(20.seconds)

    val stateManagers = Actors.init

    val router = MainRouter(stateManagers)

    val address = "0.0.0.0"
    val port = 8080
    val boundServer = Http().newServerAt(address, port).bind(router.routes)

    boundServer.onComplete {
      case Success(binding) =>
        val boundAddress = binding.localAddress
        val boundLocation = boundAddress.getAddress
        val boundPort = boundAddress.getPort

        system.log.info("Server started at: http://" + boundLocation + ":" + boundPort)

      case Failure(exception) =>
        system.log.error(s"Failed to bind server due to: $exception")
        system.terminate()
    }
  }
}
