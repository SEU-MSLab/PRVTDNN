import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile
import firrtl.annotations.MemoryLoadFileType
/**
 * Bias and Activation module
 * Don't use SyncReadMem to generate BRAM, because it can only generate LUTRAM
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
    val sumReg  = Reg(Vec(meshColumns, Bool()))
    val validReg= RegNext(io.inBiasValid)

    when(validReg){
        biasReg := io.inBias
    }.otherwise{
        biasReg := biasReg
    }

    // Align the fixed point of bias and sum, the generated verilog will 
    // left shift biasReg and fill the high bit with sign
    plusReg := biasReg.zip(io.inSum).map{case(a,b) => (a << fixedPoint-biasFP) + b}
    // Truncate before MUX to save resource
    val truncPos = fixedPoint // trunPos is the left index of the fixed point
    val signBit  = truncPos+intNum 
    val truncWire = Wire(Vec(meshColumns, SInt(actWidth.W)))
    sumReg.zipWithIndex.map{case(a, b) => a := io.inSum(b)(signBit)}

    truncWire.zipWithIndex.foreach{case(x, index) =>
        // Overflow detecton, this can only detect overflow in BA stage, if overflow
        // happens in SA stage, it can't be detected.
        when(~plusReg(index)(signBit) & sumReg(index) & biasReg(index)(biasWidth-1)){
            // underflow
            x := (scala.math.pow(2,actWidth-1)-1).toInt.S
        }.elsewhen(plusReg(index)(signBit) & ~sumReg(index) & ~biasReg(index)(biasWidth-1)){
            // Overflow
            x := (-scala.math.pow(2,actWidth-1)).toInt.S
        }.otherwise{
            x := plusReg(index)(signBit, signBit - (actWidth-1)).asSInt}
        }

    // Activation function
    if(actFunc == "ReLU"){
        actReg  := truncWire.map(x => Mux(x >= 0.S, x, 0.S))
    }else if(actFunc == "Linear"){
        actReg          := truncWire
        // io.outSigAddr   := DontCare
    }else if(actFunc == "Sigmoid"){
        io.outSigAddr.zipWithIndex.map{case(a,b) => a := truncWire(b).asUInt}
        // actReg.zipWithIndex.map{case(a, b) => a := io.inSigData(b)}
        actReg := io.inSigData
    }

    io.outAct := actReg;
}
