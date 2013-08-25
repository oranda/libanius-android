import com.oranda.libanius.Conf
import scala.util._
import scala.collection.mutable._

object ThrowawayTest {
  def main(args: Array[String]) {
    Conf.setUpForTest()
    //Util.stopwatch(println("Throwaway Test"), "println Throwaway Test")
    //Util.stopwatch(Seq.fill(10000000)(Random.nextInt), "fill with Random") // 3535,3147,3399 ms
    //Util.stopwatch((Seq.fill(10000000)(Random.nextInt)).toList, "fill with Random") // 11575,9472,12356 ms
    //Util.stopwatch(makeListOfRandoms, "makeListOfRandoms") // 12867,12775,12584 ms
    //Util.stopwatch(makeArrayBufOfRandoms, "makeArrayBufOfRandoms") // 4745,4763,4904 ms
    //Util.stopwatch(makeArrayBufOfRandoms.toList, "makeArrayBufOfRandoms") //  11858, 11866 ms
    testStreamSlice()
    
  }
  
  def testStreamSlice() {
    //println(Seq(1,2,3,4).view(1,3).slice(0,1))
    
    val arrayBuf = makeArrayBufOfRandoms
    val stream = arrayBuf.toStream
    val values = stream.slice(100, 200).toList
    println("values: " + values)
  }
  
  def makeListOfRandoms {
    
    var randoms = List[Int]()
    for (i <- 0 until 10000000)
      randoms = Random.nextInt :: randoms
    randoms
  }
  
  def makeArrayBufOfRandoms = {
    
    var randoms = ArrayBuffer[Int]()
    for (i <- 0 until 10000000)
      randoms += Random.nextInt
    randoms
  }
}