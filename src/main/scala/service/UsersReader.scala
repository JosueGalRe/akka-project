package dev.galre.josue.steamreviews
package service

import repository.entity.UserActor.GetUserInfo

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }

object UsersReader {
  def props(userManagerActor: ActorRef): Props
  = Props(new UsersReader(userManagerActor))
}

class UsersReader(userManagerActor: ActorRef)
  extends Actor
  with ActorLogging {

  override def receive: Receive = {

    case getUserCommand: GetUserInfo =>
      userManagerActor.forward(getUserCommand)
  }
}
