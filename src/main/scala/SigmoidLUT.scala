import chisel3._
import chisel3.util._ 
import chisel3.util.experimental.loadMemoryFromFile
import firrtl.annotations.MemoryLoadFileType

/**
 * 实现Sigmoid函数
 * 一开始想共享LUTRAM，但是消耗了8%的LUT，太夸张，推荐是64位以下的RAM采用LUT
 * 如果使用BRAM来实现，那要每个Bias模块里塞一个。不过BRAM的双端口输出可以2个共享
 * 之所以不在每个BA模块里添加sigmoid，因为initial要手动复制，这样每层都需要复制一次
 * 将超过4的溢出控制逻辑搬到这里
 */

class SigmoidLUT (actWidth: Int = 16, sigmoidDepth: Int = 4096,
                connectNum: Int = 35, file: String = "/tansigData", intNum: Int = 3
                    ) extends Module{
    val io = IO(new Bundle{
        val inAddr      = Input(Vec(connectNum, UInt(actWidth.W)))
        val outData     = Output(Vec(connectNum, SInt(actWidth.W)))
        
    })
        val fileName = file + "_" + sigmoidDepth + ".bin"
        require(intNum >= 2, "intNum must not smaller than 2")
        val sigmoidWidth = log2Ceil(sigmoidDepth)
        // 如果用Mem，会根本无法综合，有太多MUX
        val memory = SyncReadMem(sigmoidDepth,  SInt(actWidth.W))
        loadMemoryFromFile(memory, fileName, MemoryLoadFileType.Binary)
        val memIndex = Wire(Vec(connectNum, UInt(sigmoidWidth.W)))
        io.inAddr.zip(memIndex)foreach{ case(a,b) =>
            when(a(actWidth-1, actWidth-intNum+1).xorR){
                // 发生了溢出
                when(a(actWidth-1) === false.B){ b := (scala.math.pow(2,sigmoidWidth-1)-1).toInt.U} // 最大值是第2047个，在MATLAB里是第2048
                .otherwise{ b := scala.math.pow(2,sigmoidWidth-1).toInt.U} // 最小值是第2048个，在MATLAB里是第2049
            }.otherwise{
                // 需要保证寻址有2位整数位
                b := Cat(a(actWidth-1), a(actWidth-intNum, actWidth-intNum-(sigmoidWidth-2)))
            }
        }
    
        // 输出打拍的寄存器
        val outReg = Reg(Vec(connectNum, SInt(actWidth.W)))
        outReg.zip(memIndex).map{case(a, b) => a := memory.read(b)}
        io.outData.zip(outReg).map{case(a, b) => a := b}
        // 不打拍直接输出
        // io.outData.zip(io.inAddr).map{case(a, b) => a := memory.read(b(actWidth-1, actWidth-log2Ceil(sigmoidDepth)))}
}