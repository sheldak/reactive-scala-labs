package EShop.lab6

import io.gatling.core.Predef.{Simulation, StringBody, jsonFile, rampUsers, scenario, _}
import io.gatling.http.Predef.http

import scala.concurrent.duration._

class HttpProductCatalogGatlingTest extends Simulation {
  val httpProtocol = http  //values here are adjusted to cluster_demo.sh script
    .baseUrls("http://localhost:9123")
    .acceptHeader("text/plain,text/html,application/json,application/xml;")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

  val scn = scenario("BasicSimulation")
    .feed(jsonFile(classOf[HttpProductCatalogGatlingTest].getResource("/data/items_data.json").getPath).random)
    .exec(
      http("search")
        .post("/work")
        .body(StringBody("""{ "brand": "${brand}", "productKeyWords": ["${productKeyWords}"] }"""))
        .asJson
    )
    .pause(5)

  setUp(
    scn.inject(rampUsers(5).during(10.seconds))
  ).protocols(httpProtocol)
}
