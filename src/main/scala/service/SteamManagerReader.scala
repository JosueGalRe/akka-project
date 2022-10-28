package dev.galre.josue.akkaProject
package service

import repository.ReviewManagerActor.{ GetAllReviewsByAuthor, GetAllReviewsByGame }
import repository.entity.GameActor.GetGameInfo
import repository.entity.ReviewActor.GetReviewInfo
import repository.entity.UserActor.{ GetUserInfo, UserCreated, UserUpdated }

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{ EventEnvelope, PersistenceQuery }
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import akka.util.Timeout

import scala.concurrent.ExecutionContext

object SteamManagerReader {
  def props()(implicit system: ActorSystem, timeout: Timeout, executionContext: ExecutionContext): Props = Props(new SteamManagerReader())
}

class SteamManagerReader()(implicit system: ActorSystem, timeout: Timeout, executionContext: ExecutionContext)
  extends Actor
  with ActorLogging {

  private val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
  private val session     = CassandraSessionRegistry(system).sessionFor("akka.projection.cassandra.session-config")

  override def receive: Receive = {

    case GetUserInfo(userId) =>
      val persistenceIds = readJournal.currentEventsByPersistenceId(s"steam-userId-$userId", 0, Long.MaxValue)

      persistenceIds.runForeach(
        eventEnvelope => {
          val EventEnvelope(_, _, _, event) = eventEnvelope
          event match {
            case UserCreated(user) => println(user)

            case UserUpdated(user) => println(user)

            case any: Any => println(any)
          }
        }
      )

      sender() ! Left("Testing")

    case getGameCommand: GetGameInfo =>
      sender() ! Left("Testing")

    case getReviewCommand: GetReviewInfo =>
      sender() ! Left("Testing")

    case getAllReviewsByUserCommand: GetAllReviewsByAuthor =>
      sender() ! Left("Testing")

    case getAllReviewsByGameCommand: GetAllReviewsByGame =>
      sender() ! Left("Testing")

  }
}
