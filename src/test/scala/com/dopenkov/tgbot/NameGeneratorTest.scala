package com.dopenkov.tgbot

import com.google.gson.Gson
import org.scalatest.FlatSpec

/**
  *
  * @author Dmitry Openkov
  */
class NameGeneratorTest extends FlatSpec {

  "NameGeneratorTest" should "create a nickname" in {
    val nickname = new NameGenerator(new Gson()).createNickname
    val result = nickname.unsafeRunSync()
    println(result)
    result match {
      case Left(err) => fail(err)
      case Right(nick) => assert(nick.length <= 8)
    }

  }

}
