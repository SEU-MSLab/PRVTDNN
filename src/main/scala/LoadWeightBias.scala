import chisel3._
import chisel3.util._ 
/**
  * 
  * 根据地址分割确定哪里是Weight，哪里是Bias
  * 分割方案：如(12,10,15,10,8)的配置，则前120行是第一个SA的weight，然后150行第二个SA的weight
  */

// TODO: 目前代码逻辑还比较乱，可考虑重构。生成的是LUT RAM而不是BRAM，是因为chisel的问题
class BramBundle(addrWidth:Int = 10, dataWidth:Int = 8) extends Bundle{
    val wrData  = Input(SInt(dataWidth.W))
    val rdData  = Output(SInt(dataWidth.W))
    val wrAddr  = Input(UInt(addrWidth.W))
    val rdAddr  = Input(UInt(addrWidth.W))
    val wrEn    = Input(Bool())
}

class LoadWeightBias(layerConfig: List[Int] = List(12,10,15,10,8),
                   weightWidth: Int = 16, biasWidth: Int = 16) extends Module{
    val layerNum    = layerConfig.size - 1
    val saColumns   = layerConfig.tail
    val saRows      = layerConfig.init
    val maxColumns  = saColumns.max  // 第一个元素是输入，不算进去
    val maxRows     = saRows.max
    val weightDepth = layerConfig.zip(layerConfig.tail).map{case (a, b) => a * b}.reduce(_ + _)
    val biasDepth   = saColumns.reduce(_ + _)
    val bramDepth   = weightDepth + biasDepth
    val addrWidth   = log2Ceil(bramDepth)
    val dataWidth   = weightWidth.max(biasWidth)
    val io = IO(new Bundle{
        
        val bramIF      = new BramBundle(addrWidth, dataWidth)
        val outWeight   = Output(Vec(maxColumns, SInt(weightWidth.W)))
        val outBias     = Output(Vec(maxColumns, SInt(biasWidth.W)))
        val outWtValid  = Output(UInt(layerNum.W))
        val outBiasValid= Output(UInt(layerNum.W))
        val load        = Input(Bool())
    })


/************Bram接口****************/
    
    val mem = SyncReadMem(bramDepth, SInt(dataWidth.W))
    io.bramIF.rdData := DontCare
   
    when(io.bramIF.wrEn) {
        mem.write(io.bramIF.wrAddr, io.bramIF.wrData)
    }
    io.bramIF.rdData := mem.read(io.bramIF.rdAddr)
    

/***********************************/

/***********加载权重*****************/

    // 这里用个wtValidReg其实是因为Chisel不能subword赋值，另一种解决方法是将outWtValid定义成Vec
    val wtValidReg  = Reg(UInt(layerNum.W))
    val biasValidReg= Reg(UInt(layerNum.W))
    // 下面2个计数器控制加载权重的计数
    val layerCnt    = Counter(layerNum)
    val rowCnt      = Counter(maxRows)
    val columnCnt   = Counter(maxColumns) 
    val weightReg   = Reg(Vec(maxColumns, SInt(weightWidth.W)))
    val biasReg     = Reg(Vec(maxColumns, SInt(biasWidth.W)))

    // 软件端要确保load和写bram不要冲突，先写bram再load
    // 使用状态机实现，load的高电平时间无所谓，顶多重跑一次状态机
    val idle :: loadW :: loadB :: Nil = Enum(3)
    val stateReg    = RegInit(idle)  
    val addrReg     = RegInit(0.U(addrWidth.W))
    val loadWFinReg, loadBFinReg = RegInit(false.B)

    switch(stateReg){
        is(idle){
            when(io.load)       { stateReg := loadW }
            .otherwise          { stateReg := idle  }
        }
        is(loadW) {
            when(loadWFinReg)   { stateReg := loadB }
            .otherwise          { stateReg := loadW }
        }
        is(loadB) {
            when(loadBFinReg)   { stateReg := idle  }
            .otherwise          { stateReg := loadB }
        }
    }

    // Verilog里其实不会生成saRowsReg和saColumnsReg这2个寄存器，而是都用常量，说明它也知道这个没有驱动
    // 之所以用这2个reg是因为layerCnt.value.litValue.toInt没法过firrtl
    val saRowsReg       = Reg(Vec(layerNum, UInt()))
    val saColumnsReg    = Reg(Vec(layerNum, UInt()))
    saRowsReg.zipWithIndex.map{case (x, index) => x := saRows(index).U}
    saColumnsReg.zipWithIndex.map{case (x, index) => x := saColumns(index).U}
    val switchFlagW      = Wire(Bool())
    switchFlagW := (layerCnt.value === (layerNum-1).U) && 
            (rowCnt.value === saRowsReg(layerCnt.value)-1.U) &&
            (columnCnt.value === saColumnsReg(layerCnt.value)-1.U)
    val switchFlagB      = Wire(Bool())
    switchFlagB := (layerCnt.value === (layerNum-1).U) && 
            (columnCnt.value === saColumnsReg(layerCnt.value)-1.U)
    val memRdDataWire   = Wire(SInt(dataWidth.W))
    
    when(stateReg === idle){
        memRdDataWire := mem.read(0.U)
    }.otherwise{
        // 这里的+1会导致bram的第一格给不出来，因此加个判断
        memRdDataWire := mem.read(addrReg + 1.U)
    }
    
    when(stateReg === loadW){
        biasValidReg := 0.U  
        // 2个计数器的控制逻辑，注意边界条件
        when(switchFlagW || loadWFinReg){
            loadWFinReg := 1.U
            layerCnt.reset()
            rowCnt.reset()
            columnCnt.reset()
        }.otherwise{
            loadWFinReg := 0.U
            when((rowCnt.value === saRowsReg(layerCnt.value)-1.U) && 
                   (columnCnt.value === saColumnsReg(layerCnt.value)-1.U)){
                layerCnt.inc()
                rowCnt.reset()
                columnCnt.reset()
            }.otherwise{
                when(columnCnt.value === saColumnsReg(layerCnt.value)-1.U){
                    rowCnt.inc()
                    columnCnt.reset()
                }.otherwise {
                    columnCnt.inc()
                }
            }
        }
        // 加载权重
        when(!switchFlagW) { addrReg := addrReg + 1.U }
        // 状态机切换状态需要额外一拍，为了避免weightReg(0)又被赋值，所以加个判断
        when(!loadWFinReg) {weightReg(columnCnt.value) := memRdDataWire}
        when(columnCnt.value === saColumnsReg(layerCnt.value)-1.U){
            wtValidReg := UIntToOH(layerCnt.value)
        }.otherwise{
            wtValidReg := 0.U
        }
    }.elsewhen(stateReg === loadB){
        wtValidReg := 0.U
        when(switchFlagB){
            loadBFinReg := 1.U
            columnCnt.reset()
        }.otherwise{
            when(columnCnt.value === saColumnsReg(layerCnt.value)-1.U){
                layerCnt.inc()
                columnCnt.reset()
            }.otherwise{
                columnCnt.inc()
            }
        }
        addrReg := addrReg + 1.U
        biasReg(columnCnt.value) := memRdDataWire
        when(columnCnt.value === saColumnsReg(layerCnt.value)-1.U){
            biasValidReg := UIntToOH(layerCnt.value)
        }.otherwise{
            biasValidReg := 0.U
        }
    }.otherwise{
        loadWFinReg := 0.U
        loadBFinReg := 0.U
        weightReg.map(_ := 0.S)
        biasReg.map(_ := 0.S)
        layerCnt.reset()
        rowCnt.reset()
        columnCnt.reset()
        wtValidReg := 0.U
        biasValidReg := 0.U  
        addrReg := 0.U
    }

    io.outWeight    := weightReg
    io.outBias      := biasReg
    io.outWtValid   := wtValidReg
    io.outBiasValid := biasValidReg

}
