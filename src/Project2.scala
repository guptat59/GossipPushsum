import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.ActorRef
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import scala.Array._

object Project2 {

  val gossip = "gossip"
  val pushsum = "push-sum"
  val line = "line"
  val threeD = "3d"
  val full = "full"
  val imp3D = "imp3d"

  sealed trait Seal
  case class setNeighbours(neighs: Array[Int]) extends Seal
  case class pushsumMsg(s: Double, w: Double) extends Seal
  case class gossipMsg(rumour: String) extends Seal
  case class startPush() extends Seal
  case class initWeightNSum(s: Double, w: Double) extends Seal

  var actors = new ArrayBuffer[ActorRef]()
  var convergedActors = new ArrayBuffer[ActorRef]()
  val actorsMap = new collection.mutable.HashMap[String, Int]

  val actorNamePrefix = "tarun"
  val defaultSum = 10.0
  val maxGossipCount = 200
  var randActorIndex = 13
  var startTime = System.currentTimeMillis()

  val printName = "printName"
  val printNeighbours = "printNeighbours"
  var sumEstConvRatio = 0.0000000001

  def main(args: Array[String]): Unit = {

    var numNodes = 20
    var topology = line
    topology = full
    topology = threeD
    topology = imp3D
    var algo = gossip
    algo = pushsum

    if (args.length > 0) {
      numNodes = args(0).toInt
      topology = args(1)
      algo = args(2)
    }

    if (topology.equals(threeD) || topology.equals(imp3D)) {
      print("\n3D topology detected. Updating number of nodes from " + numNodes)
      numNodes = (math.pow(math.ceil(math.pow(numNodes.toDouble, 1.0 / 3.0)).toInt, 3)).toInt
      print(" to " + numNodes + "\n")
    }

    start(topology, algo, numNodes)
  }

  def start(topology: String, algo: String, numNodes: Int): Unit = {

    val system = ActorSystem("ActorSys")
    val namingPrefix: String = "akka://ActorSys/"

    printf("\nCreating %d actors in %s topology", numNodes, topology)

    var fullNetwork = range(0, numNodes - 1).toBuffer

    for (i <- 0 to numNodes - 1) {
      var actor = system.actorOf(Props(new ActorNode(namingPrefix, algo, topology)), actorNamePrefix + i);

      if (topology.equals(line)) {

        var neighs = new Array[Int](2)
        neighs = if (i == 0) Array(1) else if (i == numNodes - 1) Array(i - 1) else Array(i - 1, i + 1)
        actor ! setNeighbours(neighs)

      } else if (topology.equals(full)) {

        var neighs = { if (i == 0) range(1, numNodes) else if (i == numNodes - 1) range(0, numNodes - 1) else range(0, i) ++ range(i + 1, numNodes) }
        actor ! setNeighbours(neighs)

      } else if (topology.equals(threeD)) {
        var neighs = getNeighboursIn3d(numNodes, i)
        actor ! setNeighbours(neighs.toArray)

      } else if (topology.equals(imp3D)) {

        var neighs = getNeighboursIn3d(numNodes, i)
        neighs.append(getRandomNeighbour(neighs, numNodes))
        actor ! setNeighbours(neighs.toArray)

      }

      printf("\n Created actor %s", actor.path.name);
      actors += actor
    }
    printf("\nCreated %d actors in %s topology", actors.length, topology)

    printf("Build topology time taken : " + (System.currentTimeMillis() - startTime))
    startTime = System.currentTimeMillis();

    var actorRef = actors(randActorIndex)
    println(">>>>" + actorRef.path.name)
    actorRef ! printNeighbours

    if (algo.equals(gossip)) {
      println("\nStarting gossip\n")
      actorRef ! gossipMsg("Fire in the hole!!")
    } else {
      println("\nStarting push sum\n")
      actorRef ! startPush()
    }

  }

  def getRandomNeighbour(neighs: ArrayBuffer[Int], numNodes: Int): Int = {
    var x = Random.nextInt(numNodes)
    while (neighs.contains(x)) x = Random.nextInt(numNodes)
    x
  }

  def getNeighboursIn3d(numNodes: Int, ind: Int): ArrayBuffer[Int] = {

    var index = ind + 1
    var n = math.ceil(math.pow(numNodes.toDouble, 1.0 / 3.0)).toInt
    var nsquare = (math.pow(n, 2)).toInt

    var neighs = new ArrayBuffer[Int]()

    var isLeft = (((index % nsquare) % n == 1));
    var isRight = ((index % nsquare) % n == 0);
    var isFront = ((index / nsquare) == 0)
    var isBottom = ((((((index % nsquare) / n).toInt == n - 1) && ((index % nsquare) % n).toInt > 0)) || (index % nsquare) == 0)
    var isTop = (((index % nsquare) / n == 0) && ((index % nsquare) % n > 0)) || ((index % nsquare) == n)
    var isBack = (((index / nsquare) == n - 1) && ((index % nsquare) > 0))

    var range = 1 until (math.pow(n, 3) + 1).toInt
    // Left neighbor
    if (!isLeft && (range contains index - 1)) neighs += (index - 1)
    // Right neighbor
    if (!isRight && (range contains index + 1)) neighs += (index + 1)
    // Top neighbor
    if (!isTop && (range contains index - n)) neighs += (index - n)
    // Bottom neighbor
    if (!isBottom && (range contains index + n)) neighs += (index + n)
    // Front neighbor
    if (!isFront) {
      var front = (index - math.pow(n, 2)).toInt
      if (range contains front) neighs += (front)
    }
    // Back neighbor
    if (!isBack) {
      var back = (index + math.pow(n, 2)).toInt
      if (range contains back) neighs += (back)
    }
    // println("\n Index " + index + "Neighs " + neighs)

    for (j <- 0 to neighs.size - 1) {
      neighs(j) = neighs(j) - 1
    }
    neighs
  }

  class ActorNode(namingPrefix: String, algo: String, topology: String) extends Actor {

    var alphaNeighs = Array[Int]()
    var currentIndex = 0
    var gossipCount = 0
    var rumour = ""
    var sum = 0.0
    var weight = 1.0

    var lastSumEstimate = 0.0
    var consecutiveDiffCounter = 0

    def receive = {
      case n: setNeighbours => {
        alphaNeighs = n.neighs
        //println("Me " + self.path.name + " neighbours : " + alphaNeighs.toBuffer)
        actorsMap += (self.path.name -> 0)
        var y = self.path.name.split(actorNamePrefix)
        sum = defaultSum + y(y.length - 1).toDouble
      }

      case r: gossipMsg => {
        gossipCount += 1
        if (gossipCount <= maxGossipCount) {
          printf("Received message %s for the count : %d from : %s ", self.path.name, gossipCount, sender.path.name)

          actorsMap(self.path.name) = gossipCount
          rumour = r.rumour

          // var neighbour = actors(alphaNeighs(currentIndex % alphaNeighs.length))
          var x = Random.nextInt(alphaNeighs.length)
          var neighbour = actors(alphaNeighs(x))
          currentIndex = currentIndex + 1;
          neighbour ! gossipMsg(rumour)

        } else {
          printf("\nConverged message %s for the count : %d", self.path.name, gossipCount)
          println(actorsMap.toSeq.sorted.toString())
        }
      }

      case sp: startPush => {
        var neighbour = actors(alphaNeighs(Random.nextInt(alphaNeighs.length)))
        // half of s and w is kept by the sending actor
        sum = sum / 2
        weight = weight / 2

        neighbour ! pushsumMsg(sum, weight)
      }

      case p: pushsumMsg => {

        // add received pair to own corresponding values 
        sum += p.s
        weight += p.w

        var ratio = sum / weight;
        if (math.abs(ratio - lastSumEstimate) <= sumEstConvRatio) {
          consecutiveDiffCounter += 1
        } else {
          consecutiveDiffCounter = 0
        }

        // half of s and w is kept by the sending actor
        sum = sum / 2
        weight = weight / 2

        // This will be referred next time
        lastSumEstimate = sum / weight

        // Half is placed in the message. 
        var neighbour = actors(alphaNeighs(Random.nextInt(alphaNeighs.length)))
        neighbour ! pushsumMsg(sum, weight)

      }

      case x: String => {
        if (x.equals(printName)) {
          print("##" + self.path.name)
        } else if (x.equals(printNeighbours)) {

          println("My name : " + self.path.name)
          print("and my neighbours are ")
          for (i <- 0 to alphaNeighs.length - 1) {
            val p = context.actorSelection(namingPrefix + "user/" + actorNamePrefix + alphaNeighs(i));
            p.tell(printName, self)
          }
        }
      }
    }
  }

  def terminate(): Unit = {
    println("Time taken for protocol to converge : " + (System.currentTimeMillis() - startTime))
    println(actorsMap.toSeq.sorted.toString())
  }

}