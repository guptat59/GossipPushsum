import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.ActorRef
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

object Project2 {

  val gossip = "gossip"
  val pushsum = "push-sum"
  val line = "line"
  val threed = "3d"
  val full = "full"
  val imp3d = "imp3d"

  sealed trait Seal
  case class setNeighbours(neighs: Array[Int]) extends Seal
  case class printNeighbours() extends Seal
  case class printName() extends Seal
  case class pushsumMsg() extends Seal
  case class gossipMsg(rumour: String) extends Seal
  case class startGossip() extends Seal

  var actors = new ArrayBuffer[ActorRef]()
  var convergedActors = new ArrayBuffer[ActorRef]()
  val actorsMap = new collection.mutable.HashMap[String, Int]

  val actorNamePrefix = "tarun"
  val maxGossipCount = 50
  var startTime = System.currentTimeMillis()

  def main(args: Array[String]): Unit = {

    var numNodes = 20
    var topology = line
    var algo = "gossip"

    if (args.length > 0) {
      numNodes = args(0).toInt
      topology = args(1)
      algo = args(2)
    }
    start(topology, algo, numNodes)
  }

  def start(topology: String, algo: String, numNodes: Int): Unit = {

    val system = ActorSystem("ActorSys")
    val namingPrefix: String = "akka://ActorSys/"

    printf("\nCreating %d actors in %s topology", numNodes, topology)

    for (i <- 0 to numNodes - 1) {
      var actor = system.actorOf(Props(new ActorNode(namingPrefix, algo, topology)), actorNamePrefix + i);

      if (topology.equals(line)) {
        var neighs = new Array[Int](2)
        neighs = if (i == 0) Array(1)
        else if (i == numNodes - 1) Array(i - 1)
        else Array(i - 1, i + 1)
        actor ! setNeighbours(neighs);
      }

      printf("\n Created actor %s", actor.path.name);
      actors += actor
    }
    printf("\nCreated %d actors in %s topology", actors.length, topology)

    printf("Build topology time taken : " + (System.currentTimeMillis() - startTime))
    startTime = System.currentTimeMillis();

    var actorRef = actors(1)
    println(">>>>" + actorRef.path.name)
    actorRef ! printNeighbours()
    actorRef ! gossipMsg("Fire in the hole!!")

  }

  class ActorNode(namingPrefix: String, algo: String, topology: String) extends Actor {

    var alphaNeighs = Array[Int]()
    var currentIndex = 0
    var gossipCount = 0
    var rumour = "";

    def receive = {
      case n: setNeighbours => {
        alphaNeighs = n.neighs
        println("Neigh length : " + n.neighs.length + " alpha length : " + alphaNeighs.length)
        actorsMap += (self.path.name -> 0)
      }
      case pname: printName => {
        print("##" + self.path.name)
      }
      case pn: printNeighbours => {
        println("My name : " + self.path.name)
        print("and my neighbours are ")
        for (i <- 0 to alphaNeighs.length - 1) {
          val p = context.actorSelection(namingPrefix + "user/" + actorNamePrefix + alphaNeighs(i));
          p.tell(printName(), self)
        }
      }
      case r: gossipMsg => {
        gossipCount += 1
        if (gossipCount <= maxGossipCount) {
          printf("\nReceived message %s for the count : %d", self.path.name, gossipCount)

          actorsMap(self.path.name) = gossipCount
          rumour = r.rumour
          if (topology.equals(line)) {
            //          var neighbour = actors(alphaNeighs(currentIndex % alphaNeighs.length))
            var neighbour = actors(alphaNeighs(Random.nextInt(alphaNeighs.length)))
            currentIndex = currentIndex + 1;
            neighbour ! gossipMsg(rumour)
          }

          if (gossipCount == maxGossipCount) {
            convergedActors.append(self)
            if (convergedActors.length >= (actors.length / 2) - 1) {
              terminate()
              context.system.shutdown()
            }
          }

        } else {
          printf("\nConverged message %s for the count : %d", self.path.name, gossipCount)
          println(actorsMap.toString())
        }
      }

      case x: String => {
        if (x != self.path.name) {
          print(self.path.name + "called by " + x);
          val p = context.actorSelection(namingPrefix + x);
          print(p.pathString);
        } else {
          print("both are equal")
        }
      }
    }
  }

  def terminate(): Unit = {
    println("Time taken for protocol to converge : " + (System.currentTimeMillis() - startTime))
    println(actorsMap.toString())
  }

}