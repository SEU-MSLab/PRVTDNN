import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile
import firrtl.annotations.MemoryLoadFileType
/**
 * 添加Bias和应用激活函数以及截位
 * bias的引入不需要用Vec了，用移位寄存器吧
 * 不能用SyncReadMem来产生BRAM，因为只能生成LUTRAM
 */


class BiasActivation(accWidth: Int = 32, actWidth: Int = 16,
                    biasWidth: Int = 16,  fixedPoint: Int = 27, biasFP: Int = 13,
                    meshColumns: Int = 4, intNum: Int = 1, actFunc: String = "Sigmoid",
                    sigmoidDepth: Int = 4096) extends Module{
    var connectNum = meshColumns
    if(actFunc != "Sigmoid"){ connectNum = 0 }
    val io = IO(new Bundle{
        val inSum       = Input(Vec(meshColumns, SInt(accWidth.W)))
        val outAct      = Output(Vec(meshColumns, SInt(actWidth.W)))
        val inBias      = Input(Vec(meshColumns, SInt(biasWidth.W)))
        val inBiasValid = Input(Bool())
        val outSigAddr  = Output(Vec(connectNum, UInt(actWidth.W)))
        val inSigData   = Input(Vec(connectNum, SInt(actWidth.W)))
    })


    val biasReg = Reg(Vec(meshColumns, SInt(biasWidth.W)))
    val plusReg = Reg(Vec(meshColumns, SInt(accWidth.W)))
    val actReg  = Reg(Vec(meshColumns, SInt(actWidth.W)))
    val sumReg  = Reg(Vec(meshColumns, Bool())) // 为了进行溢出判断，将符号位多打一拍
    val validReg= RegNext(io.inBiasValid)






    // TODO: 这部分逻辑或许能用ShiftRegister代替
    // when(io.inBiasValid === 1.U){
    //     biasReg(0) := io.inBias
    //     for(i <- 1 until meshColumns){
    //         biasReg(i) := biasReg(i-1)
    //     }
    // } .otherwise{
    //     biasReg := biasReg
    // }

    when(validReg){
        biasReg := io.inBias
    }.otherwise{
        biasReg := biasReg
    }

    // 将偏置与sum的二进制点对齐，生成代码里会将biasReg左移指定位，并将高位补符号
    plusReg := biasReg.zip(io.inSum).map{case(a,b) => (a << fixedPoint-biasFP) + b}
    // 截取，先截取再判断，可以节省很多判断的逻辑
    val truncPos = fixedPoint // trunPos是二进制点左边那位的索引值！
    val signBit  = truncPos+intNum // 符号位
    val truncWire = Wire(Vec(meshColumns, SInt(actWidth.W)))
    sumReg.zipWithIndex.map{case(a, b) => a := io.inSum(b)(signBit)}

    truncWire.zipWithIndex.foreach{case(x, index) =>
        // 进行溢出判断，这只能判断BA阶段是否溢出，如果在SA中已经溢出，就没有办法判断成功
        // 在SA里是不能添加溢出处理的，sigmoid是因为溢出后差别不大所以才做此处理
        // 后面在sigmoid里添加了溢出判断，所以这里似乎没什么必要了
        when(~plusReg(index)(signBit) & sumReg(index) & biasReg(index)(biasWidth-1)){
            // 向下溢出
            x := (scala.math.pow(2,actWidth-1)-1).toInt.S
        }.elsewhen(plusReg(index)(signBit) & ~sumReg(index) & ~biasReg(index)(biasWidth-1)){
            // 向上溢出
            x := (-scala.math.pow(2,actWidth-1)).toInt.S
        }.otherwise{
            x := plusReg(index)(signBit, signBit - (actWidth-1)).asSInt}
        }


    // 激活函数
    if(actFunc == "ReLU"){
        // 这里已经是先截取再做MUX的判断了，已经是节省资源的方式
        actReg  := truncWire.map(x => Mux(x >= 0.S, x, 0.S))
    }else if(actFunc == "Linear"){
        actReg          := truncWire
        // io.outSigAddr   := DontCare
    }else if(actFunc == "Sigmoid"){
        // 截取位数放到Sidmoid模块中处理
        io.outSigAddr.zipWithIndex.map{case(a,b) => a := truncWire(b).asUInt}
        // actReg.zipWithIndex.map{case(a, b) => a := io.inSigData(b)}
        actReg := io.inSigData
    }

    io.outAct := actReg;
}



// 使用VecInit生成ROM的方法
// vivado可以将下面综合成ROM，但是发现没法用BRAM来实现，可能因为都是MUX，识别不出来BRAM
// 单ROM例化多次
// val rom = Module(new SigmoidROM(meshColumns, sigmoidDepth, 16, 16, file))
// rom.io.inIndex.zipWithIndex.map{case(a, b) => a := truncWire(b).asUInt}
// class SigmoidROM(sigmoidDepth: Int = 4096, sigmoidWidth: Int = 16,
//                 actWidth: Int = 16, file: String = "tansigDataInt.dat") extends Module {
//     val io = IO(new Bundle{
//         val inIndex = Input(UInt(actWidth.W))
//         val outData = Output(SInt(actWidth.W))
//     })
//     val sigmoidSource = scala.io.Source.fromResource(file)
//     val sigmoidData   = try sigmoidSource.getLines.toList.map(_.toInt) finally sigmoidSource.close()

//     val rom = VecInit.tabulate(sigmoidDepth)(x => sigmoidData(x).S)
//     val pipeReg = Reg(SInt(actWidth.W))
//     pipeReg     := rom(io.inIndex)
//     io.outData  := pipeReg
// }


// 例化一次，但综合资源和多ROM例化多次一样
// class SigmoidROM(connectNum: Int = 4, sigmoidDepth: Int = 4096, sigmoidWidth: Int = 16,
//                 actWidth: Int = 16, file: String = "tansigDataInt.dat") extends Module {
//     val io = IO(new Bundle{
//         val inIndex = Input(Vec(connectNum, UInt(actWidth.W)))
//         val outData = Output(Vec(connectNum, SInt(actWidth.W)))
//     })
//     val sigmoidSource = scala.io.Source.fromResource(file)
//     val sigmoidData   = try sigmoidSource.getLines.toList.map(_.toInt) finally sigmoidSource.close()

//     val rom = VecInit.tabulate(sigmoidDepth)(x => sigmoidData(x).S)
//     val pipeReg = Reg(Vec(connectNum, SInt(actWidth.W)))
//     pipeReg.zipWithIndex.map{case(a, b) => a := rom(io.inIndex(b))}
//     io.outData.zipWithIndex.map{case(a, b) => a := pipeReg(b)}
// }
