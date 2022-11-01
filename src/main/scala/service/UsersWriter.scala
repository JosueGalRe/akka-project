package dev.galre.josue.steamreviews
package service

import repository.UserManagerActor.CreateUserFromCSV
import repository.entity.UserActor.{ CreateUser, DeleteUser, UpdateUser }

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }

object UsersWriter {
  def props(userManagerActor: ActorRef): Props
  = Props(new UsersWriter(userManagerActor))
}

class UsersWriter(userManagerActor: ActorRef)
  extends Actor
  with ActorLogging {

  override def receive: Receive = {

    // All user messages
    case createUserCommand: CreateUser =>
      userManagerActor.forward(createUserCommand)

    case updateUserCommand: UpdateUser =>
      userManagerActor.forward(updateUserCommand)

    case deleteUserCommand: DeleteUser =>
      userManagerActor.forward(deleteUserCommand)

    case createCSVCommand: CreateUserFromCSV =>
      userManagerActor.forward(createCSVCommand)

  }
}
