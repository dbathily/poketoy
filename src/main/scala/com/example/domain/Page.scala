package com.example.domain

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source

import scala.concurrent.Future

case class Page(limit: Int, offset: Int) {
  def next(): Page = Page(limit, offset + limit)

  def fetchAll[T](f: Page => Future[(List[T], Boolean)])(implicit materializer: Materializer): Source[T, NotUsed] = {
    import materializer.executionContext
    Source.unfoldAsync((this, true)) { case (p, hasNext) =>
      if (hasNext)
        f(p).map {
          case (content, hasNext) =>
            Option(((p.next(), hasNext), content))
        }
      else
        Future.successful(Option.empty)
    }.mapConcat { list => list }
  }
}

object Page {
  def zero(limit: Int = 100): Page = Page(limit, 0)
}