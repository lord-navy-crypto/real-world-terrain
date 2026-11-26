import { generateTerrainGrid } from "./terrain-worker-core.js";

const activeRequests = new Set();
const cancelledRequests = new Set();

function validRequestId(value) {
  return typeof value === "string" && value.length > 0 && value.length <= 64;
}

function sendError(requestId, message) {
  self.postMessage({
    type: "error",
    requestId,
    message
  });
}

self.addEventListener("message", event => handleMessage(event.data));

async function handleMessage(message) {
  if (!message || typeof message !== "object" || Array.isArray(message)) {
    sendError(null, "Malformed terrain worker message");
    return;
  }

  const requestId = validRequestId(message.requestId) ? message.requestId : null;

  if (message.type === "cancel") {
    if (!requestId) {
      sendError(null, "Cancellation requires a valid request ID");
      return;
    }
    if (activeRequests.has(requestId)) cancelledRequests.add(requestId);
    return;
  }

  if (message.type !== "generate" || !requestId) {
    sendError(requestId, "Unsupported terrain worker message");
    return;
  }

  if (activeRequests.has(requestId)) {
    sendError(requestId, "A terrain request with this ID is already running");
    return;
  }

  activeRequests.add(requestId);
  try {
    const map = await generateTerrainGrid(message.options, {
      onProgress: progress => {
        self.postMessage({ type: "progress", requestId, progress });
      },
      isCancelled: () => cancelledRequests.has(requestId)
    });

    if (map.cancelled) {
      self.postMessage({ type: "cancelled", requestId });
      return;
    }

    self.postMessage({
      type: "complete",
      requestId,
      map
    }, [
      map.heights.buffer,
      map.temperatures.buffer,
      map.humidities.buffer
    ]);
  } catch (error) {
    const errorMessage = error && typeof error.message === "string"
      ? error.message
      : "Terrain generation failed";
    sendError(requestId, errorMessage);
  } finally {
    activeRequests.delete(requestId);
    cancelledRequests.delete(requestId);
  }
}
