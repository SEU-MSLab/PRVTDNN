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
/**
 * Instantiate multiple SA to implement PRVTDNN
 * The input should be n elements in(n = polyphase)
 */

class PRVTDNN(polyphase: Int = 8, layerConfig: List[Int] = List(12,10,15,10,8),
            fixedPoint: List[Int] = List(27, 26, 26, 26), biasFP: Int = 13,
            actWidth: Int = 16, weightWidth: Int = 16,biasWidth: Int = 16, 
            accWidth: Int = 32, intNum: List[Int] = List(1,1,1,1),
            actFunc: String = "Sigmoid", sigmoidDepth: Int = 4096) extends Module{
    require(layerConfig(0) >= polyphase && layerConfig(0) % 2 == 0, "First layer input error!" )
    require(layerConfig.last == polyphase, "Last layer output error!")
    val maxColumns = layerConfig.tail.max  // The first element is the number of input samples
    val layerNum = layerConfig.size - 1
    val io = IO(new Bundle{
        val inAct       = Input(Vec(polyphase, SInt(actWidth.W)))
        val outAct      = Output(Vec(polyphase, SInt(actWidth.W)))
        val inWeight    = Input(Vec(maxColumns, SInt(weightWidth.W)))
        val inBias      = Input(Vec(maxColumns, SInt(biasWidth.W)))
        val inWtValid   = Input(UInt(layerNum.W))
        val inBiasValid = Input(UInt(layerNum.W))
    })

    // Because the weight and bias latch for a cycle, so the valid signal 
    // of BiasActivation and PE should be delayed for a cycle
    val weightReg = Reg(Vec(maxColumns, SInt(weightWidth.W)))
    weightReg   := io.inWeight
    val biasReg   = Reg(Vec(maxColumns, SInt(biasWidth.W)))
    biasReg     := io.inBias

    // construct the input of first layer, use unequal width to reduce the number of Reg
    // every element in Vec need the same width, so use Seq
    // the input is x3 -> inputReg(0),x2 -> inputReg(1),
    // x1->inputReg(2) ,x0 -> inputReg(3)，x3 first, for shifting, real and imag part of 
    // complex number are put together
    val inputReg    = Seq.tabulate(layerConfig.head)(i => Reg(Vec(i+1, SInt(actWidth.W))))
    val outputReg   = Seq.tabulate(layerConfig.last)(i => Reg(Vec(i+1, SInt(actWidth.W))))

    // Put the number of polyphase of input sample to the number of neurons of first layer
    val reMapWire = Wire(Vec(layerConfig(0), SInt(actWidth.W)))
    for(i <- 0 until polyphase)                 reMapWire(i) := io.inAct(i)
    for(i <- polyphase until layerConfig(0))    reMapWire(i) := inputReg(i-polyphase)(0)

    // fill can only create replicated elements, so we use tabulate
    val SA = Seq.tabulate(layerNum)(x => Module(new SystolicArray(actWidth, weightWidth, accWidth, layerConfig(x), layerConfig(x+1))))
    val BA = Seq.tabulate(layerNum)(x => {
        if(x == layerNum-1)
            Module(new BiasActivation(accWidth = accWidth, actWidth = actWidth, biasWidth = biasWidth,
                                     fixedPoint = fixedPoint(x), biasFP = biasFP, 
                                     meshColumns = layerConfig(x+1), intNum = intNum(x), actFunc = "Linear"))
        else
            Module(new BiasActivation(accWidth = accWidth, actWidth = actWidth, biasWidth = biasWidth,
                                     fixedPoint = fixedPoint(x), biasFP = biasFP, 
                                     meshColumns = layerConfig(x+1), intNum = intNum(x), actFunc = actFunc))
        })



    SA.map(_.io.inWeight).map(x => x := weightReg.asTypeOf(chiselTypeOf(x)))
    SA.map(_.io.inwtValid).zipWithIndex.foreach{case (x, index) =>
        x := io.inWtValid(index)
    }
    BA.map(_.io.inBias).map(x => x := biasReg.asTypeOf(chiselTypeOf(x)))
    BA.map(_.io.inBiasValid).zipWithIndex.foreach{case (x, index) =>
        x := io.inBiasValid(index)
    }

    // Shift, when there is only one element in inputReg, do nothing
    inputReg.map(x => x.zip(x.tail).foreach{case (a, b) => b := a}) // 移位寄存器
    inputReg.map(_(0)).zipWithIndex.map{case (x, index) => x := reMapWire(index)} 
    outputReg.map(x => x.zip(x.tail).foreach{case (a, b) => b := a})
    outputReg.map(_(0)).zipWithIndex.map{case (x, index) => x := BA(layerNum-1).io.outAct(polyphase - index - 1)}
    // Handle the last layer
    io.outAct := outputReg.map(_.last)

    // The connection of other layers, don't change the signal name just for using <>
    for(i <- 0 until layerNum){
        if(i == 0) SA(0).io.inAct := inputReg.map(_.last)
        else       SA(i).io.inAct := BA(i-1).io.outAct
        BA(i).io.inSum := SA(i).io.outSum
    }

    // Connect the BA module to Sigmoid Table
    var assigned = 0
    if(actFunc == "Sigmoid"){
        val SL = Module(new SigmoidLUT(actWidth = actWidth, sigmoidDepth = sigmoidDepth, 
                                        connectNum = layerConfig.init.tail.sum, file = "/tansigData"))
        for(i <- 1 until layerNum){
            for(j<-0 until layerConfig(i)){
                SL.io.inAddr(j+assigned):= BA(i-1).io.outSigAddr(j)
                BA(i-1).io.inSigData(j) := SL.io.outData(j+assigned) 
            }
            assigned = assigned + layerConfig(i)
        }
    }


}
