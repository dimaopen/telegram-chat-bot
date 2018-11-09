package com.dopenkov.tgbot.storage

import java.time.Instant

import cats.effect.IO
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.dopenkov.tgbot.model.{ChatMessage, Chatter, ChatterState, Room}
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

  override def findChatter(userId: Int): IO[Option[Chatter]] = {
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
      Scanamo.exec(client)(Table[User]("Chat").put(user)).map {
        case Right(usr) => usr.toChatter
        case Left(err) =>
          logger.error("Error update user ({}): {}", chatter.id, err: Any)
          chatter
      }.getOrElse(chatter)
    }
  }

  override def listChatters(room: String): IO[List[Chatter]] = {
    queryIndex[User, Chatter]('sortKey -> s"usr" and 'data -> room, _.toChatter)
  }

  override def listRooms(): IO[List[Room]] = {
    queryIndex[MyRoom, Room]('sortKey -> "room", _.toRoom)
  }

  private def queryIndex[A: DynamoFormat, B](query: Query[_], f: A => B): IO[List[B]] = {
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
      Scanamo.exec(client)(Table[MyRoom]("Chat")
        .update('id -> myRoom.id and 'sortKey -> myRoom.sortKey, add('numberOfChatters -> inc)))
    } flatMap {
      case Left(err) =>
        logger.error("Error inc room: {} + {}, {}", room, inc, err: Any)
        IO.pure(room)
      case Right(newRoom) => IO.pure(newRoom.toRoom)
    }
  }

  override def newMessage(msg: ChatMessage): IO[ChatMessage] = {
    val myMessage: MyMessage = MyMessage(msg)
    IO {
      logger.info("New chatMessage: {}", myMessage)
      Scanamo.exec(client)(Table[MyMessage]("Chat").put(myMessage)).map {
        case Right(myMsg) => myMsg.toMessage
        case Left(err) =>
          logger.error("Error putting chatMessage ({}): {}", msg.id, err: Any)
          msg
      }.getOrElse(msg)
    }
  }
}

object User {
  private def getRoomName(chatter: Chatter) = chatter.room.getOrElse("No room")

  def apply(chatter: Chatter): User = User(s"usr-${chatter.id}", data = getRoomName(chatter), nick = chatter.nick,
    state = chatter.state.toString, realName = chatter.realName, chatId = chatter.chatId)
}

case class User(id: String, data: String, nick: String, realName: String, state: String, chatId: Long,
                sortKey: String = "usr") {
  def toChatter: Chatter = Chatter(this.id.substring(4).toInt, this.nick, ChatterState.withName(this.state),
    this.realName, toRoomOption, this.chatId)

  private def toRoomOption: Option[String] = if (this.data == "No room") None else Some(this.data)
}

case class MyMessage(id: String, data: String, text: String, nick: String, chatterId: Int, room: String,
                     sortKey: String) {
  def toMessage = ChatMessage(this.id.substring(4), this.chatterId, Instant.parse(this.data), this.nick, this.room, this.text)
}

object MyMessage {
  def apply(msg: ChatMessage): MyMessage =
    MyMessage(s"msg-${msg.id}", data = msg.timestamp.toString, msg.text, msg.nick, msg.chatterId, msg.room,
      sortKey = s"msg-${msg.room}")
}

case class MyRoom(id: String, data: String, numberOfChatters: Int, sortKey: String = "room") {
  def this(room: Room) = this(s"room-${room.name}", data = room.name, room.numberOfChatters)

  def toRoom = Room(data, numberOfChatters)
}