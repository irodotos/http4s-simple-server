import cats._
import cats.effect._
import cats.implicits._
import org.http4s.circe._
import org.http4s._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.dsl._
import org.http4s.dsl.impl._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.server._

import scala.util.Try
import java.time.Year
import java.util.UUID
import scala.collection.mutable
import org.http4s.blaze.server.BlazeServerBuilder

object Main extends IOApp {
  type Actor = String

  case class Movie(id: String, title: String, year: Int, actors: List[String], director: String)

  case class Director(firstName: String, lastName: String) {
    override def toString: String = s"$firstName $lastName"
  }

  case class DirectorDetails(name: String, lastName: String, genre: String)

  val snjl: Movie = Movie(
    "6bcbca1e-efd3-411d-9f7c-14b872444fce",
    "Zack Snyder's Justice League",
    2021,
    List("Henry Cavill", "Gal Godot", "Ezra Miller", "Ben Affleck", "Ray Fisher", "Jason Momoa"),
    "Zack Snyder"
  )

  val moviesDB: Map[String, Movie] = Map(snjl.id -> snjl)

  private def findMovieById(movieId: UUID) =
    moviesDB.get(movieId.toString)

  private def findMoviesByDirector(director: String): List[Movie] =
    moviesDB.values.filter(_.director == director).toList


  //  GET /movies?director=Zack%20Snyder&year=2021
  implicit val yearQueryParamDecoder: QueryParamDecoder[Year] =
    QueryParamDecoder[Int].emap(year =>
      Try(Year.of(year))
        .toEither
        .leftMap(e => ParseFailure("Invalid year", e.getMessage)))
  object DirectorQueryParamMatcher extends QueryParamDecoderMatcher[String]("director")
  object YearQueryParamMatcher extends OptionalValidatingQueryParamDecoderMatcher[Year]("year")

  def movieRoutes[F[_] : Monad]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "movies" :? DirectorQueryParamMatcher(director) +& YearQueryParamMatcher(maybeYear) => {
        val moviesByDirector = findMoviesByDirector(director)
        maybeYear match {
          case Some(year) => year.fold(
            _ => BadRequest("Invalid year"),
            y => {
              val moviesByDirectorAndYear = moviesByDirector.filter(_.year == y.getValue)
              Ok(moviesByDirectorAndYear.asJson)
            }
          )
          case None => Ok(moviesByDirector.asJson)
        }
      }
      case GET -> Root / "movies" / UUIDVar(movieId) / "actors" => {
        findMovieById(movieId).map(_.actors) match {
          case Some(actors) => Ok(actors.asJson)
          case None => NotFound(s"No movie found with id $movieId")
        }
      }
    }
  }

  object DirectorPath {
    def unapply(str: String): Option[Director] = {
      Try {
        val tokens = str.split(" ")
        Director(tokens(0), tokens(1))
      }.toOption
    }
  }

  val directorDetailsDB: mutable.Map[Director, DirectorDetails] = mutable.Map(
    Director("Zack", "Snyder") -> DirectorDetails("Zack" , "Snyder", "superhero"),
    Director("Christopher", "Nolan") -> DirectorDetails("Christopher" , "Nolan", "realistic")
  )

  def directorRoutes[F[_] : Monad]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "directors" / DirectorPath(director) => {
        directorDetailsDB.get(director) match {
          case Some(details) => Ok(details.asJson)
          case _ => NotFound(s"No director found with name $director")
        }
      }
    }
  }

  def allRoutes[F[_] : Monad]: HttpRoutes[F] = {
    movieRoutes[F] <+> directorRoutes[F]  // <+> is a method from cats.syntax.SemigroupK
  }

  def allRoutesComplete[F[_] : Monad]: HttpApp[F] = {
    allRoutes[F].orNotFound
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val apis = Router(
      "/api/movies" -> movieRoutes[IO],
      "/api/directors" -> directorRoutes[IO]
    ).orNotFound

    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(allRoutesComplete)
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
}