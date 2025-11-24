package helper

import config.GraphWrite
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.metrics.Counter

/** Simple metric stage that counts how many GraphWrite records flow through. */
class CountWritesFunction extends RichMapFunction[GraphWrite, GraphWrite] {

  @transient private var counter: Counter = _

  override def open(parameters: Configuration): Unit = {
    // metrics name: graphrag.graph_writes_total
    val group = getRuntimeContext.getMetricGroup.addGroup("graphrag")
    counter = group.counter("graph_writes_total")
  }

  override def map(value: GraphWrite): GraphWrite = {
    if (counter != null) {
      counter.inc()
    }
    value
  }
}
