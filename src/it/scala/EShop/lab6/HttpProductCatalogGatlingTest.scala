package EShop.lab6

import io.gatling.core.Predef.{Simulation, StringBody, jsonFile, rampUsers, scenario, _}
import io.gatling.http.Predef.http

import scala.concurrent.duration._

class HttpProductCatalogGatlingTest extends Simulation {
  val httpProtocol = http  //values here are adjusted to cluster_demo.sh script
    .baseUrls(
      "http://localhost:9123"
      // "http://localhost:9001",
      // "http://localhost:9002",
      // "http://localhost:9003"
    )
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
    scn.inject(rampUsers(2250).during(10.seconds))
  ).protocols(httpProtocol)
}

// local: przy 2250 sa faile (33), ale 2135 szybko (< 800s)
// cluster: przy 5000 jest juz kilka failed (6), duzy czas oczekiwania dla tych, co przeszly