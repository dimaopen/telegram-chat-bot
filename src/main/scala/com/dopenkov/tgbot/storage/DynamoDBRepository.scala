package com.dopenkov.tgbot.storage

import cats.effect.IO
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.dopenkov.tgbot.model.{Chatter, ChatterState, Room}
import com.gu.scanamo._
import com.gu.scanamo.query.Query
import com.gu.scanamo.syntax._
import org.apache.logging.log4j.{LogManager, Logger}

/**
  *
  * @author Dmitry Openkov
  */
class DynamoDBRepository(client: AmazonDynamoDB) extends Repository {
  val logger: Logger = LogManager.getLogger(getClass)

  override def findChatter(userId: String): IO[Option[Chatter]] = {
    IO {
      Scanamo.exec(client)(Table[User]("Chat").get('id -> s"usr-$userId" and 'sortKey -> "usr"))
    } map {
      case Some(Right(user)) => Some(user.toChatter)
      case Some(Left(err)) =>
        logger.error("Error getting a user ({}): {}", userId, err: Any)
        None
      case _ => None
    }
  }

  override def updateChatter(chatter: Chatter): IO[Chatter] = {
    val user = User(chatter)
    IO {
      logger.info("Updating chatter: {}", user)
      Scanamo.exec(client)(Table[User]("Chat").put(user))
      chatter
    }
  }

  override def listChatters(room: String): IO[List[Chatter]] = {
    implicit val f = (_:User).toChatter
    queryIndex[User, Chatter]('sortKey -> s"usr" and 'data -> room)
  }

  override def listRooms(): IO[List[Room]] = {
    implicit val f = (_:MyRoom).toRoom
    queryIndex[MyRoom, Room]('sortKey -> "room")
  }

  private def queryIndex[A: DynamoFormat, B](query: Query[_])(implicit f: A => B):IO[List[B]] = {
    val gsi1 = Table[A]("Chat").index("sortKey-data-index")
    IO {
      Scanamo.exec(client)(gsi1.query(query))
    } map {
      list =>
        list.collectFirst { case Left(err) => err } match {
          case Some(err) =>
            logger.error("Error query index: {}", err)
            List()
          case None => list.collect { case Right(entity) => entity } map f
        }
    }
  }

  override def findRoom(roomName: String): IO[Option[Room]] =
    IO {
      Scanamo.exec(client)(Table[MyRoom]("Chat").get('id -> s"room-$roomName" and 'sortKey -> "room"))
    } map {
      case Some(Right(myRoom)) => Some(myRoom.toRoom)
      case Some(Left(err)) =>
        logger.error("Error getting a room ({}): {}", roomName, err: Any)
        None
      case _ => None
    }

  override def incNumberOfChatters(room: Room, inc: Int): IO[Room] = {
    val myRoom: MyRoom = new MyRoom(room)
    IO {
      Scanamo.exec(client)(Table[MyRoom]("Chat").given('data -> myRoom.data)
        .update('id -> myRoom.id and 'sortKey -> myRoom.sortKey, add('numberOfChatters -> inc)))
    } flatMap {
      case Left(err) =>
        logger.error("Error inc room: {} + {}, {}", room, inc, err: Any)
        IO.pure(room)
      case Right(newRoom) => IO.pure(newRoom.toRoom)
    }
  }
}

object User {
  private def getRoomName(chatter: Chatter) = chatter.room.getOrElse("No room")

  def apply(chatter: Chatter): User = new User(s"usr-${chatter.id}", data = getRoomName(chatter), nick = chatter.nick,
    state = chatter.state.toString, realName = chatter.realName, chatId = chatter.chatId)
}

case class User(id: String, data: String, nick: String, realName: String, state: String, chatId: Long, sortKey: String = "usr") {


  def toChatter: Chatter = Chatter(this.id.substring(4), this.nick, ChatterState.withName(this.state), this.realName,
    toRoomOption, this.chatId)

  private def toRoomOption: Option[String] = if (this.data == "No room") None else Some(this.data)
}

case class MyRoom(id: String, data: String, numberOfChatters: Int, sortKey: String = "room") {
  def this(room: Room) = this(s"room-${room.name}", data = room.name, room.numberOfChatters)

  def toRoom = Room(data, numberOfChatters)
}