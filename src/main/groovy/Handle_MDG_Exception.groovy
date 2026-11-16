import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import java.time.Instant

def Message processData(Message message) {
    def ex = message.getProperties().get("CamelExceptionCaught")
    message.setHeader("CamelHttpResponseCode", 500)
    message.setHeader("Content-Type", "application/json")
    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson([
        error: "MDG_PIPELINE_EXECUTION_FAILED", timestamp: Instant.now().toString(),
        details: ex ? ex.getMessage() : "Unhandled MDG Pipeline Error"
    ])))
    return message
}
