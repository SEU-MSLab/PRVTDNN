import chisel3._
import chisel3.util._ 
/**
  * The processing element in Systolic array
  * Weight and Partial Sum reuse the same bus, even they are not the same width
  */

class PE(actWidth: Int = 16, weightWidth: Int = 16, accWidth: Int = 32) extends Module{
    val io = IO(new Bundle{
        val inAct       = Input(SInt(actWidth.W))
        val inWtPS      = Input(SInt(accWidth.W))
        val outWtPS     = Output(SInt(accWidth.W))
        val outAct      = Output(SInt(actWidth.W))
        val inwtValid   = Input((Bool()))
    })

    // Avoid reset by using Reg instead of Reginit
    val weightReg   = Reg(SInt(weightWidth.W))
    val actReg      = Reg(SInt(actWidth.W))
    val parSumReg   = Reg(SInt(accWidth.W))

    actReg    := io.inAct;
    parSumReg := io.inWtPS + weightReg * actReg;
    io.outAct := actReg;

    when(io.inwtValid === 1.U){
        weightReg   := io.inWtPS
        io.outWtPS  := weightReg
    } .otherwise {
        weightReg   := weightReg
        io.outWtPS  := parSumReg
    }
}


