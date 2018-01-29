package service

import http.TvShowApi
import http.SeasonApi
import http.PeopleApi
import model.{ TvShow, Member, Season, Epsiode }
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import exceptions.TooManyRequestsException
import java.util.concurrent.TimeUnit

trait TvShowService extends TvShowApi with SeasonApi with PeopleApi {
  
  def getTop(n: Integer) = getTopRated().map( _ take n)
  
  def getTvShowInfosAndCast(tvShow: TvShow) = {
    val tvShowWithDetails = getTvShowDetails(tvShow)
    val castWithDetails =  getCast(tvShow).flatMap(getCastDetails)
    tvShowWithDetails zip castWithDetails
  }
  
  
  //Backoff strategy -- The api accepts only 40 requests every 10 seconds
  private def getAcotorDetail (actor: Member,  retryAfter: Int = 0):Future[Member] = {
    if(retryAfter > 0) TimeUnit.SECONDS.sleep(retryAfter)
    getDetail(actor).transformWith{
        case Success(s) => Future{s}
        case Failure(ex) => {
          ex match {
            case ex: TooManyRequestsException => getAcotorDetail(actor,ex.retryAfter)
            case _ => Future.failed(ex)
          }
        }
      }
  }
  
  //Backoff strategy
  private def getSeasonDetail(tvShow: TvShow, season: Season, retryAfter: Int = 0): Future[Season] = {
    if(retryAfter > 0) TimeUnit.SECONDS.sleep(retryAfter)
      getDetails(tvShow, season).transformWith{
        case Success(s) => Future{s}
        case Failure(ex) => {
          ex match {
            case ex: TooManyRequestsException => getSeasonDetail(tvShow,season, ex.retryAfter)
            case _ =>  Future.failed(ex)
          }
        }
      }
  }
  
  private def getCastDetails(cast: List[Member]) = {
    val castWithDetails = cast.map(getAcotorDetail(_)) 
    Future.foldLeft(castWithDetails)(List.empty[Member]){ (acc, actor) => actor :: acc }
  }

  private def getSeasonsDetails(tvShow: TvShow): Future[List[Season]] = {
    val seasons = tvShow.seasons.map(getSeasonDetail(tvShow, _))
    Future.foldLeft(seasons)(List.empty[Season]){ (acc, season) => season :: acc }
  }
  
  private def getTvShowDetails(tvShow: TvShow): Future[TvShow] = {
    for {
      tvShowWithSeaons <- getDetails(tvShow)
      seasonsWithEpisodes <- getSeasonsDetails(tvShowWithSeaons)
    } yield tvShowWithSeaons.copy(seasons = seasonsWithEpisodes)
  }
  
}