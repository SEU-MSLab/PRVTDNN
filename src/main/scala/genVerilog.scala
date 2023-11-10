import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage.{ChiselStage, FirtoolOption}

object genVerilog extends App{
  (new ChiselStage).execute(
    Array("--target", "systemverilog"),
    Seq(ChiselGeneratorAnnotation(() => new RVTDNNTop),
      FirtoolOption("--disable-all-randomization")))
}
