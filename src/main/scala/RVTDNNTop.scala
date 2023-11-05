import chisel3._
import chisel3.util._ 

/**
  * RVTDNN的封装，包括RVTDNN+参数加载模块
  * 不是所有参数都要封装到顶层
  */

class RVTDNNTop extends Module{
/***********参数配置表*************/
    val layerConfig = List(20,10,10,10,8)
    val polyphase   = 8 // 表示一次有4个sample输入
    val actFP       = 14 // 输入激励的二进制点位置
    val wtFP        = 13 // 权重的二进制点
    val biasFP      = 13 // 偏置的二进制点
    val initFP      = actFP + wtFP // 定点位数，只有第一层一定是这个，其他层与intNum有关
    // intNum是截取后的整数位数，如S1.14，则给1，S2.13，则给2
    // 如果是sigmoid，激活函数输入范围通常在-4到4，因此给全2
    // 最后一位固定得是1，因为DPD输出总是S1.14
    val intNum      = List(2, 2, 2, 1) 
    // Bias相加需要移动的位数，
    val actWidth    = 16 // ADC数据位宽
    val weightWidth = 16 // 权重位宽
    val biasWidth   = 16 // 偏置位宽
    val accWidth    = 40 // 累加和位宽
    val actFunc     = "ReLU"
    val sigmoidDepth= 32768
    var fixedPoint  = List.fill(layerConfig.size-1)(initFP)
/*********************************/
    require(layerConfig.size-1 == intNum.size, "The integer number don't match the layer configuration!")
    require(actFunc == "Sigmoid" || actFunc == "ReLU", "Not supported activation function" )
    
    if(actFunc == "Sigmoid"){
        // 如果是sigmoid，因为会将输出压缩到-1到1之间，因此定点位每次都不变
        fixedPoint = List.fill(layerConfig.size-1)(initFP)
    }else{
        // 如果激活函数输出超过1，则会导致每次定点的移动，是累加的过程
        fixedPoint  = List(initFP) ++ List.tabulate(intNum.size-1)(x => initFP-intNum(x)+1)
    }
    
    val loadModule  = Module(new LoadWeightBias(layerConfig = layerConfig, 
                            weightWidth = weightWidth, biasWidth = biasWidth))
    val rvtdnnModule= Module(new RVTDNN(polyphase = polyphase, layerConfig = layerConfig,
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

    /*********外部连线************/
    loadModule.io.bramIF    <> io.bramIF
    loadModule.io.load      := io.load
    rvtdnnModule.io.inAct   := io.inAct
    io.outAct               := rvtdnnModule.io.outAct    

    /*********内部连线************/
    rvtdnnModule.io.inWeight    := loadModule.io.outWeight
    rvtdnnModule.io.inBias      := loadModule.io.outBias
    rvtdnnModule.io.inWtValid   := loadModule.io.outWtValid
    rvtdnnModule.io.inBiasValid := loadModule.io.outBiasValid

}
