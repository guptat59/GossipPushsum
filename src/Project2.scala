import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.ActorRef
import scala.collection.mutable.ArrayBuffer

object Project2 {

  sealed trait Seal
  case class setNeighbours(neighs: Array[Int]) extends Seal
  case class printNeighbours() extends Seal
  case class printName() extends Seal
  case class sendMessage() extends Seal
  case class startGossip() extends Seal
  case class recieveMessage() extends Seal

  val actorNamePrefix = "tarun"

  def main(args: Array[String]): Unit = {

    var numNodes = 10
    var topology = "Line"
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

    var actors = new ArrayBuffer[ActorRef]()

    printf("\nCreating %d actors in %s topology", numNodes, topology)

    for (i <- 1 to numNodes) {
      var actor = system.actorOf(Props(new ActorNode(namingPrefix)), actorNamePrefix + i);

      if (topology.equals("Line")) {
        var neighs = new Array[Int](2)
        neighs = if (i == 1) Array(2)
        else if (i == numNodes) Array(i - 1)
        else Array(i - 1, i + 1)
        actor ! setNeighbours(neighs);
      }

      printf("\n Created actor %s", actor.path.name);
      actors += actor
    }
    printf("\nCreated %d actors in %s topology", actors.length, topology)

    //for (i <- 0 to actors.length - 1) {

    var actorRef = actors(5)
    println(">>>>" + actorRef.path.name)
    actorRef ! printNeighbours()
    actorRef ! sendMessage()
    //}

  }

  def startPoint(): Unit = {
    println("Hello world")
  }

  class ActorNode(namingPrefix: String) extends Actor {

    var neighbours = Array[Int]()

    def receive = {
      case n: setNeighbours => {
        neighbours = n.neighs;
      }
      case pname: printName => print("##" + self.path.name)
      case pn: printNeighbours => {
        println("My name : " + self.path.name)
        print("and my neighbours are ")
        for (i <- 0 to neighbours.length - 1) {
          val p = context.actorSelection(namingPrefix + "user/" +  actorNamePrefix + neighbours(i));
          println("\n" + p.pathString)
          p.tell(printName(),self)
        }
      }
      case s: sendMessage => {
       // neighbours(0) ! recieveMessage()
        //neighbours(1) ! recieveMessage()
        
      }
      case r: recieveMessage => {
          println("recieved message: " +self.path.name);
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

}