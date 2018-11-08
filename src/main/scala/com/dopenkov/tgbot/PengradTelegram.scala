package com.dopenkov.tgbot

import cats.effect.IO
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.response.SendResponse

/**
  *
  * @author Dmitry Openkov
  */
class PengradTelegram (token: String) extends Telegram {
  private val tg = new TelegramBot(token)
  //        val callback: Callback[SendMessage, MessagesResponse] = new NullCallback()
  //        tg.execute[SendMessage, MessagesResponse](message, callback)

  def execute(request: SendMessage): IO[SendResponse] = IO {tg.execute(request)}
}
