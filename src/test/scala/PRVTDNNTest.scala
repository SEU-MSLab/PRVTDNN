import chiseltest._
import chisel3._
import chisel3.util._
import org.scalatest.flatspec.AnyFlatSpec

class PRVTDNNTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "RVTDNNTest"
  ignore should "pass" in {
    test(new PRVTDNNTop).withAnnotations(
        Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
          dut.clock.setTimeout(0)
          // dut.io.bramIF.enable.poke(true.B)
          val bramSource = scala.io.Source.fromResource("bramData.dat")
          val bramData  = try bramSource.getLines.toList.map(_.toInt) finally bramSource.close()
          val actSource = scala.io.Source.fromResource("actData.dat")
          val actData   = try actSource.getLines.toList.map(_.toInt) finally actSource.close()

          for(i <- 0 until dut.loadModule.bramDepth){
              dut.io.bramIF.wrAddr.poke(i.U)
              dut.io.bramIF.wrData.poke(bramData(i).S)
              dut.io.bramIF.wrEn.poke(true.B)
              dut.clock.step()
          }
          dut.io.bramIF.wrEn.poke(false.B)
          var bramOutput: List[Int] = List()
          for(i <- 0 until dut.loadModule.bramDepth){
              dut.io.bramIF.rdAddr.poke(i.U)
              dut.clock.step()
              dut.io.bramIF.rdData.expect(bramData(i).S)
              // 也可以直接 println(dut.io.bramIF.rdData.peek())，不需要考虑类型
              bramOutput = dut.io.bramIF.rdData.peek().litValue.toInt +: bramOutput
          }
          println("load Weight and Bias finished! ")
          println("Test of Bram success!")
          dut.io.load.poke(true.B)
          dut.clock.step(100)
          dut.io.load.poke(false.B)
          dut.clock.step(1000)
          // 
          
          for(i <- Range(0, actData.length, dut.polyphase)){
              for(j <- 1 to dut.polyphase) {
                dut.io.inAct(j-1).poke((actData(i + dut.polyphase-j).S))
              }
              dut.clock.step()
          }
          // 从第一组数据输入到第一个数据输出是118ps，即59个时钟周期，等于

          dut.io.outAct.peek()
          dut.clock.step(3000)
    }
  }
}

