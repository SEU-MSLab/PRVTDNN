/*
MIT License

Copyright (c) 2023 Microwave System Lab @ Southeast University.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
import chisel3._
import chisel3.util._ 
/*
Divide the RAM to Weights and Biases 
Solution：for (12,10,15,10,8)，the first 120 entry is the Weights for the first SA,
the next 150 entry is the Weights for the second SA, and so on.
*/

// TODO: rewrite the code to generate BRAM instead of LUTRAM
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
    val maxColumns  = saColumns.max  // The first element is input
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


/************BRAM Interface****************/
    
    val mem = SyncReadMem(bramDepth, SInt(dataWidth.W))
    io.bramIF.rdData := DontCare
   
    when(io.bramIF.wrEn) {
        mem.write(io.bramIF.wrAddr, io.bramIF.wrData)
    }
    io.bramIF.rdData := mem.read(io.bramIF.rdAddr)
    

/***********************************/

/***********Load Weights and Biases*****************/

    // Use wtValidReg because Chisel can't assign subword, another solution is define outWtvalid as Vec
    val wtValidReg  = Reg(UInt(layerNum.W))
    val biasValidReg= Reg(UInt(layerNum.W))
    // The following two counter control the loading weight
    val layerCnt    = Counter(layerNum)
    val rowCnt      = Counter(maxRows)
    val columnCnt   = Counter(maxColumns) 
    val weightReg   = Reg(Vec(maxColumns, SInt(weightWidth.W)))
    val biasReg     = Reg(Vec(maxColumns, SInt(biasWidth.W)))

    // Software side should ensure load and write bram don't conflict, write bram first then load
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

    // In the generated verilog, there will not be saRowsReg and saColumnsReg, instead, 
    // it will use constant because it knows there is no driver for them
    // The reason why use these 2 regs is because layerCnt.value.litValue.toInt can't pass firrtl
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
        // the +1 here will cause the first entry of bram can't be read, so add a judge
        memRdDataWire := mem.read(addrReg + 1.U)
    }
    
    when(stateReg === loadW){
        biasValidReg := 0.U  
        // The control logic of two counters,
        // pay attention to the boundary condition
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
        // Load weights
        when(!switchFlagW) { addrReg := addrReg + 1.U }
        // The state Machine need one more cycle to switch state
        // to avoid weightReg(0) be assigned again, so add a judge
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
