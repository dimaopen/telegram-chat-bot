package com.dopenkov.tgbot.model

import com.dopenkov.tgbot.model.MessageType.MessageType
import com.pengrad.telegrambot.model._

/**
  *
  * @author Dmitry Openkov
  */
object Helper {
  implicit class RichUpdate (val self: Update) {
    def content: (java.io.Serializable, MessageType) = {
      List(
        self.message() -> MessageType.Message,
        self.editedMessage() -> MessageType.EditedMessage,
        self.channelPost() -> MessageType.ChannelPost,
        self.editedChannelPost() -> MessageType.EditedChannelPost,
        self.inlineQuery() -> null,
        self.chosenInlineResult() -> null,
        self.callbackQuery() -> null,
        self.shippingQuery() -> null,
        self.preCheckoutQuery() -> null,
      ).find(_._1 != null).get
    }

    def from(): User = {
      content match {
        case (m: Message, _) => m.from()
        case (m: InlineQuery, _) => m.from()
        case (m: ChosenInlineResult, _) => m.from()
        case (m: CallbackQuery, _) => m.from()
        case (m: ShippingQuery, _) => m.from()
        case (m: PreCheckoutQuery, _) => m.from()
      }
    }
  }
}
object MessageType extends Enumeration {
  type MessageType = Value
  val Message, EditedMessage, ChannelPost, EditedChannelPost = Value
}
