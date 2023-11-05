import chisel3._
import chisel3.util._ 
/**
 * 需求：需要可配置采样率抽取，实现983.04M采样率下也能跑122.88M的模型，类似FIR滤波器延迟n个周期
 */

class SystolicArray(actWidth: Int = 16, weightWidth: Int = 16, accWidth: Int = 32,
                    meshRows: Int = 4, meshColumns: Int = 4 ) extends Module{
  val io = IO(new Bundle{
    val inAct     = Input(Vec(meshRows, SInt(actWidth.W)))
    val inWeight  = Input(Vec(meshColumns, SInt(weightWidth.W)))
    val outSum    = Output(Vec(meshColumns, SInt((accWidth).W)))
    val inwtValid = Input(Bool())
  })


  // PEs(r)(c)表示第r行，第c列的PE，从0开始
  val PEs: Seq[Seq[PE]] = Seq.fill(meshRows, meshColumns)(Module(new PE(actWidth, weightWidth, accWidth)))
  // 产生PE间的互连线，但是好像根本不需要
  // val wtPSWire = Wire(Vec((meshRows-1)*meshColumns, SInt(accWidth.W)))
  // val actWire  = Wire(Vec(meshRows*(meshColumns-1), SInt(actWidth.W)))
  val validReg = RegNext(io.inwtValid)
  

  PEs.foreach(_.foreach(_.io.inwtValid := validReg));

  // 虽然不是很像用for循环来连线，但gemmini都用了
  for(row <- 0 until meshRows){
    for(col <- 0 until meshColumns){
      // 第一行的输入连接到SA端口
      if(row == 0)  PEs(row)(col).io.inWtPS := io.inWeight(col)
      else          PEs(row)(col).io.inWtPS := PEs(row-1)(col).io.outWtPS

      // 最后一行的输出连接到模块外
      if(row == meshRows-1)   io.outSum(col) := PEs(row)(col).io.outWtPS
      
      // 第一列
      if(col == 0)  PEs(row)(col).io.inAct := io.inAct(row)
      else          PEs(row)(col).io.inAct := PEs(row)(col-1).io.outAct

      // 最后一列的outAct可以不管
    }
  }
}