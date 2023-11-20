/*
MIT License

Copyright (c) 2023 Microwave System Lab @ Southeast University

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
  * Top module of PRVTDNN, including PRVTDNN and LoadWeightBias
  * not every parameter should be encapsulated to the top module
  */

class PRVTDNNTop extends Module{
    val layerConfig = List(20,10,10,10,8)
    val polyphase   = 8 // represent 4 input samples
    val actFP       = 14 // the fixed point position of activation
    val wtFP        = 13 // the fixed point position of weights
    val biasFP      = 13 // the fixed point position of biases
    val initFP      = actFP + wtFP // fixed-point position of the first layer, other layers are related to intNum
    // intNum is the number of integer bits after truncation, e.g. S1.14, then give 1, S2.13, then give 2
    // if the activation function is sigmoid, the input range is usually -4 to 4, so give 2
    // the last bit fixed to 1, since the output of DPD is always S1.14
    val intNum      = List(2, 2, 2, 1) 
    val actWidth    = 16 // bit width of activation
    val weightWidth = 16 // bit width of weight
    val biasWidth   = 16 // bit width of bias
    val accWidth    = 40 // bit width of partial sum
    val actFunc     = "ReLU"
    val sigmoidDepth= 32768
    var fixedPoint  = List.fill(layerConfig.size-1)(initFP)
/*********************************/
    require(layerConfig.size-1 == intNum.size, "The integer number don't match the layer configuration!")
    require(actFunc == "Sigmoid" || actFunc == "ReLU", "Not supported activation function" )
    
    if(actFunc == "Sigmoid"){
        // If use sigmoid, the output will compress to -1 to 1, so the fixed point position will not change
        fixedPoint = List.fill(layerConfig.size-1)(initFP)
    }else{
        // If the output of activation function larger than 1, the fixed point position will move to left
        fixedPoint  = List(initFP) ++ List.tabulate(intNum.size-1)(x => initFP-intNum(x)+1)
    }
    
    val loadModule  = Module(new LoadWeightBias(layerConfig = layerConfig, 
                            weightWidth = weightWidth, biasWidth = biasWidth))
    val prvtdnnModule= Module(new PRVTDNN(polyphase = polyphase, layerConfig = layerConfig,
                            fixedPoint = fixedPoint, biasFP = biasFP,
                            actWidth = actWidth, weightWidth = weightWidth,
                            biasWidth = biasWidth, accWidth = accWidth, 
                            intNum = intNum, actFunc = actFunc, sigmoidDepth = sigmoidDepth))

    val io = IO(new Bundle{
        val bramIF  = new BramBundle(loadModule.addrWidth, loadModule.dataWidth)
        val load    = Input(Bool())
        val inAct   = Input(Vec(polyphase, SInt(actWidth.W)))
        val outAct  = Output(Vec(polyphase, SInt(actWidth.W)))
    })

    /*********external connection************/
    loadModule.io.bramIF    <> io.bramIF
    loadModule.io.load      := io.load
    prvtdnnModule.io.inAct   := io.inAct
    io.outAct               := prvtdnnModule.io.outAct    

    /*********internal connection************/
    prvtdnnModule.io.inWeight    := loadModule.io.outWeight
    prvtdnnModule.io.inBias      := loadModule.io.outBias
    prvtdnnModule.io.inWtValid   := loadModule.io.outWtValid
    prvtdnnModule.io.inBiasValid := loadModule.io.outBiasValid

}
