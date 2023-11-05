import chisel3._
import chisel3.util._ 
/**
 * 例化多个SA，实现RVTDNN
 * 输入应该是n个元素进(n = polyphase)
 * polyphase包含了I与Q分量
 */

class RVTDNN(polyphase: Int = 8, layerConfig: List[Int] = List(12,10,15,10,8),
            fixedPoint: List[Int] = List(27, 26, 26, 26), biasFP: Int = 13,
            actWidth: Int = 16, weightWidth: Int = 16,biasWidth: Int = 16, 
            accWidth: Int = 32, intNum: List[Int] = List(1,1,1,1),
            actFunc: String = "Sigmoid", sigmoidDepth: Int = 4096) extends Module{
    require(layerConfig(0) >= polyphase && layerConfig(0) % 2 == 0, "First layer input error!" )
    require(layerConfig.last == polyphase, "Last layer output error!")
    val maxColumns = layerConfig.tail.max  // 第一个元素是输入，不算进去
    val layerNum = layerConfig.size - 1
    val io = IO(new Bundle{
        val inAct       = Input(Vec(polyphase, SInt(actWidth.W)))
        val outAct      = Output(Vec(polyphase, SInt(actWidth.W)))
        val inWeight    = Input(Vec(maxColumns, SInt(weightWidth.W)))
        val inBias      = Input(Vec(maxColumns, SInt(biasWidth.W)))
        val inWtValid   = Input(UInt(layerNum.W))
        val inBiasValid = Input(UInt(layerNum.W))
    })

    // 因为这里weight和bias打了一拍，所以BiasActivation和PE的valid也要打一拍
    val weightReg = Reg(Vec(maxColumns, SInt(weightWidth.W)))
    weightReg   := io.inWeight
    val biasReg   = Reg(Vec(maxColumns, SInt(biasWidth.W)))
    biasReg     := io.inBias

    // 构造第一层输入，使用不等位宽来减少Reg数量，Vec的每个元素需要位宽一样，因此用Seq
    // 输入方式是x3 -> inputReg(0),x2 -> inputReg(1),
    // x1->inputReg(2) ,x0 -> inputReg(3)，x3先进，方便移位，复数的实部与虚部放一起
    val inputReg    = Seq.tabulate(layerConfig.head)(i => Reg(Vec(i+1, SInt(actWidth.W))))
    val outputReg   = Seq.tabulate(layerConfig.last)(i => Reg(Vec(i+1, SInt(actWidth.W))))

    // 将polyphase个输入重新映射到第一层神经元个数
    val reMapWire = Wire(Vec(layerConfig(0), SInt(actWidth.W)))
    for(i <- 0 until polyphase)                 reMapWire(i) := io.inAct(i)
    for(i <- polyphase until layerConfig(0))    reMapWire(i) := inputReg(i-polyphase)(0)

    // fill只能创建重复元素，因此用tabulate
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



    // 生成代码可以实现阶段Vec的功能
    SA.map(_.io.inWeight).map(x => x := weightReg.asTypeOf(chiselTypeOf(x)))
    SA.map(_.io.inwtValid).zipWithIndex.foreach{case (x, index) =>
        x := io.inWtValid(index)
    }
    BA.map(_.io.inBias).map(x => x := biasReg.asTypeOf(chiselTypeOf(x)))
    BA.map(_.io.inBiasValid).zipWithIndex.foreach{case (x, index) =>
        x := io.inBiasValid(index)
    }

    // 移位，当x只有1个元素时，不会做任何操作
    inputReg.map(x => x.zip(x.tail).foreach{case (a, b) => b := a}) // 移位寄存器
    inputReg.map(_(0)).zipWithIndex.map{case (x, index) => x := reMapWire(index)} 
    outputReg.map(x => x.zip(x.tail).foreach{case (a, b) => b := a})
    outputReg.map(_(0)).zipWithIndex.map{case (x, index) => x := BA(layerNum-1).io.outAct(polyphase - index - 1)}
    // 最后一层的处理
    io.outAct := outputReg.map(_.last)

    // 其他层的连接，不要为了使用<>而将所有信号名称改成一样的，得不偿失
    for(i <- 0 until layerNum){
        if(i == 0) SA(0).io.inAct := inputReg.map(_.last)
        else       SA(i).io.inAct := BA(i-1).io.outAct
        BA(i).io.inSum := SA(i).io.outSum
    }

    // 将BA模块连接上Simgoid的BRAM阵
    // 最后一层不需要sigmoid！
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