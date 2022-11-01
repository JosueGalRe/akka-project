package dev.galre.josue.steamreviews

import akka.actor.ActorRef

package object repository {
  final case class GameController(
    actor: ActorRef,
    var name: String,
    var isDisabled: Boolean = false
  )

  final case class UserController(
    actor: ActorRef,
    var isDisabled: Boolean = false
  )

  final case class ReviewController(
    actor: ActorRef,
    userId: Long,
    steamAppId: Long,
    var isDisabled: Boolean = false
  )

}
