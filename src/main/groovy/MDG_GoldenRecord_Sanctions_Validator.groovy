import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.security.MessageDigest
import java.time.Instant

def Message processData(Message message) {
    def payload = new JsonSlurper().parseText(message.getBody(String))
    String bpName = (payload.BusinessPartnerFullName ?: "").trim()
    String taxNumber = (payload.TaxNumber1 ?: "").replaceAll("[^A-Za-z0-9]", "").toUpperCase()
    String iban = (payload.IBAN ?: "").replaceAll("\\s+", "").toUpperCase()
    String countryCode = (payload.Country ?: "").trim().toUpperCase()

    List<String> validationErrors = []
    if (!bpName || bpName.length() < 3) validationErrors.add("INVALID_BP_NAME: BusinessPartnerFullName must contain at least 3 characters.")
    if (!countryCode || countryCode.length() != 2) validationErrors.add("INVALID_COUNTRY: ISO 3166-1 alpha-2 country code is required.")
    if (!isValidIban(iban)) validationErrors.add("INVALID_IBAN_CHECKSUM: Failed ISO 13616 Mod-97 verification for IBAN [${iban}].")

    List<String> sanctionedEntities = ["SHADOW HOLDINGS LTD", "NEXUS EMBARGO CORP", "VORTEX SANCTIONED GMBH"]
    String normalizedName = bpName.toUpperCase().replaceAll("[^A-Z0-9 ]", "")
    if (sanctionedEntities.any { calculateSimilarity(normalizedName, it) >= 0.85d }) {
        validationErrors.add("COMPLIANCE_SANCTIONS_HIT: Entity matched OFAC/EU consolidated sanctions watchlist.")
    }

    String goldenRecordHash = MessageDigest.getInstance("SHA-256")
        .digest("${normalizedName}|${taxNumber}|${countryCode}".getBytes("UTF-8")).encodeHex().toString()
    message.setHeader("X-MDG-Golden-Hash", goldenRecordHash)

    if (!validationErrors.isEmpty()) {
        message.setHeader("X-MDG-Gate-Status", "BLOCKED")
        message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson([
            status: "QUARANTINED_FOR_MDG_STEWARDSHIP", timestamp: Instant.now().toString(),
            goldenRecordSha256: goldenRecordHash, businessPartnerName: bpName, violations: validationErrors
        ])))
    } else {
        message.setHeader("X-MDG-Gate-Status", "APPROVED")
        message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson([
            "@odata.context": "\$metadata#A_BusinessPartner/\$entity", BusinessPartnerCategory: "2",
            BusinessPartnerFullName: bpName, BusinessPartnerGrouping: "BP02", GoldenRecordFingerprint: goldenRecordHash,
            to_BusinessPartnerTax: [[BPTaxType: "${countryCode}0", BPTaxNumber: taxNumber]],
            to_BusinessPartnerBank: [[BankCountryKey: countryCode, IBAN: iban]],
            ComplianceVerification: [SanctionsCheck: "PASSED", IbanMod97Check: "PASSED", VerifiedAt: Instant.now().toString()]
        ])))
    }
    return message
}

private boolean isValidIban(String iban) {
    if (!iban || iban.length() < 15 || iban.length() > 34) return false
    String rearranged = iban.substring(4) + iban.substring(0, 4)
    StringBuilder numericIban = new StringBuilder()
    for (char ch : rearranged.toCharArray()) {
        if (Character.isDigit(ch)) numericIban.append(ch)
        else if (Character.isLetter(ch)) numericIban.append(Character.getNumericValue(ch))
        else return false
    }
    return new BigInteger(numericIban.toString()).mod(BigInteger.valueOf(97)).intValue() == 1
}

private double calculateSimilarity(String s1, String s2) {
    if (s1 == s2) return 1.0d
    int maxLen = Math.max(s1.length(), s2.length())
    if (maxLen == 0) return 1.0d
    int matches = 0
    for (int i = 0; i < Math.min(s1.length(), s2.length()); i++) {
        if (s1.charAt(i) == s2.charAt(i)) matches++
    }
    return (double) matches / (double) maxLen
}
