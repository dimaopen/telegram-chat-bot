name := "tg-bot"
version := "0.1"

maintainer := "Dmitry Openkov <dimaopen@gmail.com>"

scalaVersion := "2.12.7"

//aws
val aws_sdk_version = "1.11.430"
val aws_lambda_version = "1.2.0"
libraryDependencies +=  "com.amazonaws" % "aws-java-sdk-core" % aws_sdk_version
//libraryDependencies +=  "com.amazonaws" % "aws-java-sdk-ses" % aws_sdk_version
//libraryDependencies +=  "com.amazonaws" % "aws-java-sdk-s3" % aws_sdk_version
libraryDependencies +=  "com.amazonaws" % "aws-java-sdk-dynamodb" % aws_sdk_version
libraryDependencies +=  "com.amazonaws" % "aws-lambda-java-events" % aws_lambda_version intransitive()
libraryDependencies +=  "com.amazonaws" % "aws-lambda-java-core" % aws_lambda_version

//logging
libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.11.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.11.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.11.1"

//tg api lib
libraryDependencies += "com.github.pengrad" % "java-telegram-bot-api" % "4.1.0"
//dynamodb lib
libraryDependencies += "com.gu" %% "scanamo" % "1.0.0-M6"

libraryDependencies += "org.typelevel" %% "cats-effect" % "1.0.0"

//testing
resolvers += "DynamoDB Local Release Repository" at "https://s3-us-west-2.amazonaws.com/dynamodb-local/release"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test
libraryDependencies += "com.amazonaws" % "DynamoDBLocal" % "1.11.119" % Test


//packaging
import NativePackagerHelper._
import com.typesafe.sbt.packager.MappingsHelper._
enablePlugins(UniversalPlugin)

topLevelDirectory := None

mappings in Universal ++= contentOf("target/scala-2.12/classes")

mappings in Universal ++= {
  // calculate provided dependencies.
  val compileDep = (managedClasspath in Compile).value.toSet
  val runtimeDep = (managedClasspath in Runtime).value.toSet
  val deps = compileDep ++ runtimeDep

  // create mappings
  fromClasspath(deps.toSeq, "lib", artifact => true)
}

(Universal / packageBin) := ((Universal / packageBin) dependsOn (Compile / packageBin)).value