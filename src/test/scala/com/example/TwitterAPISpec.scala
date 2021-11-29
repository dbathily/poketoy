package com.example

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Sink
import com.example.domain.TwitterConf
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Minutes, Span}
import org.scalatest.wordspec.AnyWordSpec

class TwitterAPISpec extends AnyWordSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  lazy val testKit = ActorTestKit()
  implicit def typedSystem = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.classicSystem

  override implicit val patienceConfig = PatienceConfig(Span(2, Minutes))

  val twitter = new TwitterApi(TwitterConf.fromConfig(typedSystem.settings.config))

  "Twitter API" should {

    "live tweet" in {
      val limit = 1
      val tweets = twitter.track("twitter").take(limit).runWith(Sink.seq)
        .futureValue
      tweets should not be empty
      tweets.length should be(limit)
    }

  }

}
