import chiseltest._
import chisel3._
import chisel3.util._
import org.scalatest.flatspec.AnyFlatSpec

// FIXME: 因为文件交互的原因，这个test是跑不下去的
class SigmoidTest extends AnyFlatSpec with ChiselScalatestTester{
    behavior of "SigmoidTest"
    ignore should "pass" in {
        val sigmoidDepth = 4096
        val connectNum = 32
        test(new SigmoidLUT(actWidth = 16, sigmoidDepth = sigmoidDepth,
                connectNum = 35)).withAnnotations(
            Seq(VerilatorBackendAnnotation, WriteVcdAnnotation)) { dut =>
            dut.clock.setTimeout(0)
            val sigmoidSource = scala.io.Source.fromResource("tansigDataChiselTest.dat")
            val sigmoidData   = try sigmoidSource.getLines.toList.map(_.toInt) finally sigmoidSource.close()

            // 每一个入口都访问同样的数据
            for(i <- 0 until sigmoidDepth){
                for(j <- 0 to connectNum){
                    dut.io.inAddr(j).poke(i.U)
                }
                dut.clock.step()
            }
            
            // 对比数据是否一致
            for(i <- 0 until sigmoidDepth){
                dut.clock.step()
                for(j <- 0 to connectNum){
                    dut.io.outData(j).expect(sigmoidData(i).S)
                    println(dut.io.outData(j).peek())
                }
                // 也可以直接 println(dut.io.outData(j).peek())，不需要考虑类型
                
            }
            println("Test of SigmoidLUT success!")
            // 验证load后的输出
        }
    }
}