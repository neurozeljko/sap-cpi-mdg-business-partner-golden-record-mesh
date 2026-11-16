import spock.lang.Specification
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import com.sap.gateway.ip.core.customdev.util.Message

class MdgGoldenRecordValidatorSpec extends Specification {
    def script = new GroovyShell().parse(new File("src/main/groovy/MDG_GoldenRecord_Sanctions_Validator.groovy"))

    def "Should approve valid Business Partner and generate S/4HANA 2025 OData V4 payload"() {
        given:
        def msg = new Message()
        msg.setBody(JsonOutput.toJson([BusinessPartnerFullName: "Rheinmetall Industrial Systems GmbH", TaxNumber1: "DE129456789", IBAN: "DE89370400440532013000", Country: "DE"]))
        when:
        def res = script.processData(msg)
        def json = new JsonSlurper().parseText(res.getBody(String))
        then:
        res.getHeaders()["X-MDG-Gate-Status"] == "APPROVED"
        json.ComplianceVerification.IbanMod97Check == "PASSED"
    }

    def "Should quarantine Business Partner failing ISO 13616 Mod-97 IBAN verification"() {
        given:
        def msg = new Message()
        msg.setBody(JsonOutput.toJson([BusinessPartnerFullName: "Clean Logistics AG", TaxNumber1: "DE987654321", IBAN: "DE00370400440532013000", Country: "DE"]))
        when:
        def res = script.processData(msg)
        then:
        res.getHeaders()["X-MDG-Gate-Status"] == "BLOCKED"
    }

    def "Should block sanctioned entity matching OFAC/EU watchlist"() {
        given:
        def msg = new Message()
        msg.setBody(JsonOutput.toJson([BusinessPartnerFullName: "Shadow Holdings Ltd", TaxNumber1: "CY11223344", IBAN: "DE89370400440532013000", Country: "CY"]))
        when:
        def res = script.processData(msg)
        then:
        res.getHeaders()["X-MDG-Gate-Status"] == "BLOCKED"
    }
}
