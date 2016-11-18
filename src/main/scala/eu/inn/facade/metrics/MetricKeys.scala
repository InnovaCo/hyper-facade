package eu.inn.facade.metrics

object MetricKeys {
  val ACTIVE_CONNECTIONS = "http.active-connections"
  val REJECTED_CONNECTS = "http.rejected-connects"
  val WS_LIFE_TIME = "http.ws-life-time"
  val WS_MESSAGE_COUNT = "http.ws-message-count"
  val WS_STASHED_EVENTS_COUNT = "http.ws-stashed-events-count"
  val WS_STASHED_EVENTS_BUFFER_OVERFLOW_COUNT = "http.ws-stashed-events-buffer-overflow-count"
  val REQUEST_PROCESS_TIME = "request.process-time"
  val HEARTBEAT = "heartbeat"
}
