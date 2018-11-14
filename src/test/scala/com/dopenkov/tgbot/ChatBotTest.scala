package com.dopenkov.tgbot

import java.time.Instant

import cats.effect.IO
import com.dopenkov.tgbot.model.{ChatMessage, Chatter, Room}
import com.dopenkov.tgbot.storage.Repository
import com.google.gson.Gson
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.response.SendResponse
import org.scalatest.FlatSpec

import scala.collection.mutable
import scala.io.Source


/**
  *
  * @author Dmitry Openkov
  */
class ChatBotTest extends FlatSpec {

  "ChatBot" should "create a new chatter" in new Tele with Repo {
    val chatBot = createChatBot(this)
    val upd = loadMessage("incoming-messages/update-message-01.json")
    chatBot.newEvent(upd).unsafeRunSync()
    assert(findChatter(450000099).unsafeRunSync().get.realName == "Ivanhoe Stevenson")
  }

  "ChatBot" should "handle nick changing" in new Tele with Repo {
    val chatBot = createChatBot(this)
    chatBot.newEvent(loadUpdateWithText("/start")).unsafeRunSync()
    chatBot.newEvent(loadUpdateWithText("/nick")).unsafeRunSync()
    chatBot.newEvent(loadUpdateWithText("Karlsen")).unsafeRunSync()
    assert(findChatter(450000099).unsafeRunSync().get.nick == "Karlsen")
    val msg = lastMessage
    assert(msg.getParameters.get("text") == "Nick changed to Karlsen")
  }

  "ChatBot" should "handle bad nicks" in new Tele with Repo {
    val chatBot = createChatBot(this)
    chatBot.newEvent(loadUpdateWithText("/start")).unsafeRunSync()
    val msg = lastMessage
    val initialNick = getInitialNick(msg)
    chatBot.newEvent(loadUpdateWithText("/nick")).unsafeRunSync()
    chatBot.newEvent(loadUpdateWithText("$Karlsen")).unsafeRunSync()
    val last = lastMessage
    assert(last.getParameters.get("text").asInstanceOf[String].startsWith("Invalid nick:"))
    assert(findChatter(450000099).unsafeRunSync().get.nick == initialNick)
  }

  "ChatBot" should "not change nick if user cancelled" in new Tele with Repo {
    val chatBot = createChatBot(this)
    chatBot.newEvent(loadUpdateWithText("/start")).unsafeRunSync()
    val msg = lastMessage
    val initialNick = getInitialNick(msg)
    chatBot.newEvent(loadUpdateWithText("/nick")).unsafeRunSync()
    chatBot.newEvent(loadUpdateWithText("/cancel")).unsafeRunSync()
    chatBot.newEvent(loadUpdateWithText("Karlsen")).unsafeRunSync()
    assert(findChatter(450000099).unsafeRunSync().get.nick == initialNick)
  }

  private def getInitialNick(msg: SendMessage) = {
    "your nick is: ([^)]+)".r.findFirstMatchIn(msg.getParameters.get("text").toString)
      .map(m => m.group(1)).getOrElse("Not found")
  }

  private val gson = new Gson()

  private def loadMessage(resource: String): Update =
    gson.fromJson(Source.fromResource(resource).bufferedReader(), classOf[Update])

  private implicit def reflector(ref: AnyRef) = new {
    def setV(name: String, value: Any): Unit = {
      val field = ref.getClass.getDeclaredField(name)
      field.setAccessible(true)
      field.set(ref, value.asInstanceOf[AnyRef])
    }

  }

  private def loadUpdateWithText(text: String) = {
    val upd: Update = loadMessage("incoming-messages/update-message-01.json")
    upd.message().setV("text", text)
    upd
  }

  private def createChatBot(helper: Tele with Repo): ChatBot = {
    new ChatBot(helper, helper, new NameGenerator(new Gson()))
  }

  trait Repo extends Repository {
    val storage: mutable.Map[Int, Chatter] = mutable.Map.empty


    override def updateChatter(chatter: Chatter): IO[Chatter] = IO {
      storage.put(chatter.id, chatter)
      chatter
    }

    override def findChatter(userId: Int): IO[Option[Chatter]] = IO.pure(storage.get(userId))

    override def listChatters(room: String): IO[List[Chatter]] = ???

    override def newMessage(msg: ChatMessage): IO[ChatMessage] = ???

    override def findMessages(room: String, since: Instant, limit: Int): IO[List[ChatMessage]] = ???

    private val room1 = Room("Newbies", 10)
    private val room2 = Room("Advanced", 100)
    private val rooms = List(room1, room2)

    override def listRooms(): IO[List[Room]] = {
      IO.pure(rooms)
    }

    override def findRoom(roomName: String): IO[Option[Room]] = IO.pure(rooms.find(_.name == roomName))

    override def incNumberOfChatters(room: Room, inc: Int): IO[Room] = IO.pure(room.copy(numberOfChatters = room.numberOfChatters + inc))
  }

  trait Tele extends Telegram {


    val messages = mutable.ListBuffer[SendMessage]()

    override def execute(request: SendMessage): IO[SendResponse] = {
      IO.pure {
        println(request.getParameters.get("text"))
        request +=: messages
        gson.fromJson(Source.fromResource("tg-answers/ok-answer.json").bufferedReader(), classOf[SendResponse])
      }
    }

    def lastMessage: SendMessage = messages.head
  }

}
