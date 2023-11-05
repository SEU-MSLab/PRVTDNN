import chiseltest._
import chisel3._
import chisel3.util._
import org.scalatest.flatspec.AnyFlatSpec

class LoadWBTest extends AnyFlatSpec with ChiselScalatestTester{
    behavior of "LoadWeightBias"
    it should "pass" in {
        val layerConfig =  List(20,10,10,8)
        test(new LoadWeightBias(layerConfig = layerConfig)).withAnnotations(
            Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
            // 验证能否正常写入Bram与读出
            // val bramData = for{ x <- 0 until dut.bramDepth} yield (x)
            dut.clock.setTimeout(0)
            // dut.io.bramIF.enable.poke(true.B)
            for(i <- 0 until dut.bramDepth){
                dut.io.bramIF.wrAddr.poke(i.U)
                dut.io.bramIF.wrData.poke((i+1).S)
                dut.io.bramIF.wrEn.poke(true.B)
                dut.clock.step()
            }
            dut.io.bramIF.wrEn.poke(false.B)
            var bramOutput: List[Int] = List()
            for(i <- 0 until dut.bramDepth){
                dut.io.bramIF.rdAddr.poke(i.U)
                dut.clock.step()
                dut.io.bramIF.rdData.expect((i+1).S)
                // 也可以直接 println(dut.io.bramIF.rdData.peek())，不需要考虑类型
                bramOutput = dut.io.bramIF.rdData.peek().litValue.toInt +: bramOutput
            }
            println("Test of Bram success!")
            // println(bramOutput)
            // 验证load后的输出
            dut.io.load.poke(true.B)
            dut.clock.step(100)
            dut.io.load.poke(false.B)
            dut.clock.step(1000)
            // val lastValid = 1 << (dut.layerNum - 1)
            // while(dut.io.outBiasValid.peek().litValue.toInt != lastValid){
            //     if(dut.io.outWtValid.litValue.toInt != 0){
            //         println(dut.io.outWeight.peek())
            //     }
            //     if(dut.io.outBiasValid.litValue.toInt != 0){
            //         println(dut.io.outBias.peek())
            //     }
            //     dut.clock.step()
            // }
        }
    }
}