import scala.collection.mutable.ListBuffer

object TestObj {
  
  def main(args: Array[String]) {
    println("main")
    //val objWithLazyVal = new ObjWithLazyVal()
    //var listObj = List[ObjWithLazyVal]()
    //listObj ::= objWithLazyVal
    /*
    var obj = List[ProxyToEntity]()
    val proxy = new ProxyToEntity("strValues")
    obj ::= proxy
    * 
    */
    procWithState()
    //proxy.sampleMethod()
  }
  
  def procWithState() {
    var x = 3
    println(x)
  }
}
