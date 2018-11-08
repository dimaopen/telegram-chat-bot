package com.dopenkov.tgbot

import java.util.Locale

import cats.effect.IO
import cats.kernel.Comparison.EqualTo
import cats.kernel.Order
import cats.syntax.option._
import com.dopenkov.tgbot.model.Helper._
import com.dopenkov.tgbot.model.{Chatter, ChatterState, MessageType, Room}
import com.dopenkov.tgbot.storage.Repository
import com.pengrad.telegrambot.model.request.{InlineKeyboardButton, InlineKeyboardMarkup}
import com.pengrad.telegrambot.model.{CallbackQuery, Chat, Message, Update}
import com.pengrad.telegrambot.request.SendMessage
import org.apache.logging.log4j.{LogManager, Logger}

import scala.util.Random

/**
  *
  * @author Dmitry Openkov
  */
class ChatBot(telegram: Telegram, val repository: Repository) {
  private val logger: Logger = LogManager.getLogger(getClass)
  private lazy val random = Random

  def toLocale(str: String): Locale = Locale.forLanguageTag(str)


  def newEvent(upd: Update): IO[Any] = {
    val user = upd.from()

    val userId = user.id().toString
    val chatter: IO[Option[Chatter]] = repository.findChatter(userId)
    chatter flatMap {
      case None => handleNew(upd)
      case Some(ch) => handleExisting(upd, ch)
    } flatMap { op =>
      op.map(m => telegram.execute(m)).getOrElse(IO.unit)
    }
  }

  private def updateAndSend(newChatter: Chatter, text: String)(implicit chat: Chat) = {
    repository.updateChatter(newChatter)
      .map(ch => new SendMessage(chat.id, text).some)
  }

  def handleExisting(upd: Update, ch: Chatter): IO[Option[SendMessage]] = {

    upd.content match {
      case (msg: Message, MessageType.Message) =>
        implicit val chat: Chat = msg.chat()
        val command = getCommand(msg)
        (command, ch.state) match {
          case (Some(BotCommand.Nick), _) => updateAndSend(ch.copy(state = ChatterState.NickChanging),
            s"Your nick is ${ch.nick}. Enter a new nick or /cancel")
          case (Some(BotCommand.Cancel), _) => repository.updateChatter(ch.copy(state = ChatterState.General))
            .map(_ => None)
          case (Some(BotCommand.Rooms), _) =>
            (for {
              _ <- repository.updateChatter(ch.copy(state = ChatterState.General))
              rooms <- repository.listRooms()
            } yield rooms).map(rooms => createSelectRoomMessage(msg.chat.id, rooms, ch.room).some)
          case (Some(BotCommand.Who), _) =>
            ch.room match {
              case None => updateAndSend(ch.copy(state = ChatterState.General), "You are not in a room. Use /rooms command.")
              case Some(roomName) =>
                (for {
                  _ <- repository.updateChatter(ch.copy(state = ChatterState.General))
                  chatters <- repository.listChatters(roomName)
                } yield chatters) map (chatters => createChattersMessage(msg.chat.id, chatters, roomName).some)
            }
          case (Some(BotCommand.Exit), _) =>
            for {
              currentRoom <- ch.room.map(repository.findRoom).getOrElse(IO.pure(None))
              _ <- currentRoom.map(repository.incNumberOfChatters(_, -1)).getOrElse(IO.unit)
              msg <- updateAndSend(ch.copy(room = None, state = ChatterState.General), "You left the chat")
            } yield msg
          case (None, ChatterState.NickChanging) => updateAndSend(ch.copy(nick = msg.text(), state = ChatterState.General),
            s"Nick changed to ${msg.text()}")
          case (None, _) => IO.pure(new SendMessage(msg.chat().id(), s"NEW MSG: " + msg.text()).some) //todo send to everyone in a room
        }
      case (clb: CallbackQuery, _) =>
        handleCallbackQuery(ch, clb)
      case _ => IO.pure(None)
    }
  }

  private def handleCallbackQuery(ch: Chatter, clb: CallbackQuery): IO[Option[SendMessage]] = {
    //callback query are for entering to a room
    import cats.kernel.instances.string.catsKernelStdOrderForString
    val currentRoom = ch.room.getOrElse("")
    Order[String].comparison(currentRoom, clb.data()) match {
      case EqualTo => IO.pure(new SendMessage(clb.message().chat().id(), s"You are already in room '$currentRoom'").some)
      case _ =>
        repository.findRoom(clb.data()).flatMap {
          case Some(room) =>
            for {
              currentRoom <- ch.room.map(repository.findRoom).getOrElse(IO.pure(None))
              _ <- currentRoom.map(repository.incNumberOfChatters(_, -1)).getOrElse(IO.unit)
              _ <- repository.incNumberOfChatters(room, 1)
              msg <- updateAndSend(ch.copy(room = room.name.some),
                s"You are in the room '${room.name}' now.\nUse /who to see who there are.")(clb.message().chat())
            } yield msg
          case None => IO.pure(new SendMessage(clb.message().chat().id(), s"Room not found: ${clb.data()}'").some)
        }
    }
  }

  def handleNew(upd: Update): IO[Option[SendMessage]] = {
    upd.content match {
      case (msg: Message, MessageType.Message) =>
        val nick = createNick
        updateAndSend(Chatter(msg.from().id().toString, nick, ChatterState.General,
          s"${msg.from().firstName()} ${msg.from().lastName()}", None),
          s"Help message here\n your nick is: $nick")(msg.chat())
      case _ => IO.pure(None) //if not a message it means something wrong, don't react
    }
  }

  private def getCommand(msg: Message): Option[BotCommand.CommandVal] = {
    val p = "/([a-z]+)".r
    msg.text() match {
      case p(cmd) => BotCommand.fromString(cmd)
      case _ => None
    }
  }

  private def createNick = {
    "n" + random.nextPrintableChar() + random.nextPrintableChar()
  }

  private def createSelectRoomMessage(chatId: Long, rooms: List[Room], currentRoom: Option[String]): SendMessage = {
    rooms match {
      case List() => new SendMessage(chatId, "No room set. Please come in later.")
      case _ =>
        val text = currentRoom.map(r => s"You are in room '$r'.").getOrElse("You are not in a room.") + "\nChoose the room"
        new SendMessage(chatId, text)
          .replyMarkup(new InlineKeyboardMarkup(rooms.map { r =>
            new InlineKeyboardButton(s"${r.name} (${r.numberOfChatters})")
              .callbackData(r.name)
          }.toArray))
    }
  }

  private def createChattersMessage(chatId: Long, chatters: List[Chatter], currentRoom: String): SendMessage = {
    chatters match {
      case List() => new SendMessage(chatId, s"Nobody in room $currentRoom")
      case _ =>
        new SendMessage(chatId, s"You are in room $currentRoom. There are\n" + chatters.map(_.nick).mkString("\n"))
    }
  }

}

object BotCommand {

  sealed trait CommandVal

  case object Nick extends CommandVal

  case object Cancel extends CommandVal

  case object Exit extends CommandVal

  case object Rooms extends CommandVal

  case object Who extends CommandVal

  val values = Seq(Nick, Cancel, Rooms, Who, Exit)

  def fromString(cmd: String): Option[CommandVal] = BotCommand.values.find(_.toString.equalsIgnoreCase(cmd))
}