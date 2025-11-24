package helper

import config.GraphWrite
import org.apache.flink.streaming.api.datastream.DataStream

object Quality {

  /**
   * Attach a simple metrics stage that counts GraphWrite records.
   * Returns the same stream with an extra map stage.
   */
  def attachMetrics(
                     writes: DataStream[GraphWrite]
                   ): DataStream[GraphWrite] = {
    writes.map(new CountWritesFunction())
  }
}
