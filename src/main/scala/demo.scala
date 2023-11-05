import chisel3._
import chisel3.util._
import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage.{ChiselStage, FirtoolOption}

object PRVTDNNVerilog extends App{
  (new ChiselStage).execute(
    Array("--target", "systemverilog"),
    Seq(ChiselGeneratorAnnotation(() => new RVTDNNTop)),
      FirtoolOption("--disable-all-randomization"))
}
