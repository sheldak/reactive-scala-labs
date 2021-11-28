package EShop.lab6

import EShop.lab5.{ProductCatalog, SearchService}
import EShop.lab5.ProductCatalogHttpServer.{GetItems, Listing, Response}

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.{ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.Try

/**
 * Spawns an actor system that will connect with the cluster and spawn `instancesPerNode` workers
 */
class HttpProductCatalogNode {
  private val instancesPerNode = 3
  private val config           = ConfigFactory.load()

  val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ClusterWorkRouters",
    config
  )

  for (i <- 0 to instancesPerNode) system.systemActorOf(ProductCatalog(new SearchService()), s"worker$i")

  def terminate(): Unit =
    system.terminate()
}

/**
 * Spawns a seed node
 */
object ClusterNodeApp extends App {
  private val config               = ConfigFactory.load()
  private val httpWorkersNodeCount = 3

  val system = ActorSystem[Nothing](
    Behaviors.setup[Nothing] { ctx =>
      // spawn workers
      val workersNodes = for (i <- 1 to httpWorkersNodeCount)
        yield ctx.spawn(ProductCatalog(new SearchService()), s"worker$i")
      Behaviors.same
    },
    "ClusterWorkRouters",
    config
      .getConfig(Try(args(0)).getOrElse("cluster-default"))
      .withFallback(config.getConfig("cluster-default"))
  )

  Await.ready(system.whenTerminated, Duration.Inf)
}

object ProductCatalogHttpClusterApp extends App {
  val productCatalogHttpServerInCluster = new ProductCatalogHttpServerInCluster()
  productCatalogHttpServerInCluster.run(args(0).toInt)
}

/**
 * The server that distributes all of the requests to the workers registered in the cluster via the group router.
 * Will spawn `httpWorkersNodeCount` [[HttpWorkersNode]] instances that will each spawn `instancesPerNode`
 * [[RegisteredHttpWorker]] instances giving us `httpWorkersNodeCount` * `instancesPerNode` workers in total.
 *
 * @see https://doc.akka.io/docs/akka/current/typed/routers.html#group-router
 */
class ProductCatalogHttpServerInCluster() extends JsonSupport {
  private val config = ConfigFactory.load()

  implicit val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ClusterWorkRouters",
    config.getConfig("cluster-default")
  )

  implicit val scheduler        = system.scheduler
  implicit val executionContext = system.executionContext

  val workers = system.systemActorOf(Routers.group(ProductCatalog.ProductCatalogServiceKey), "clusterWorkerRouter")

  implicit val timeout: Timeout = 5.seconds

  def routes: Route =
    path("work") {
      post {
        entity(as[GetItems]) { request =>
          val response =
            workers.ask(ref => ProductCatalog.GetItems(request.brand, request.productKeyWords, ref))

          onSuccess(response) {
            case ProductCatalog.Items(items) => complete(items)
            case _                           => complete(StatusCodes.InternalServerError)
          }
        }
      }
    }

  def run(port: Int): Unit = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    StdIn.readLine() // let it run until user presses return
    // bindingFuture
    //   .flatMap(_.unbind()) // trigger unbinding from the port
    //   .onComplete { _ =>
    //     system.terminate()
    //     workersNodes.foreach(_.terminate())
    //   } // and shutdown when done
  }
}
