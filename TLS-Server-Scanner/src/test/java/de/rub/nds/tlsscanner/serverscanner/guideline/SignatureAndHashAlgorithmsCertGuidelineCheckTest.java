/*
 * TLS-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2023 Ruhr University Bochum, Paderborn University, Technology Innovation Institute, and Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package de.rub.nds.tlsscanner.serverscanner.guideline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.rub.nds.scanner.core.guideline.GuidelineAdherence;
import de.rub.nds.scanner.core.guideline.GuidelineCheckResult;
import de.rub.nds.tlsscanner.core.constants.TlsAnalyzedProperty;
import de.rub.nds.tlsscanner.serverscanner.guideline.checks.SignatureAndHashAlgorithmsCertificateGuidelineCheck;
import de.rub.nds.tlsscanner.serverscanner.report.ServerReport;
import de.rub.nds.x509attacker.constants.X509SignatureAlgorithm;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class SignatureAndHashAlgorithmsCertGuidelineCheckTest {

    @Test
    public void testPositive() {
        ServerReport report = new ServerReport("test", 443);
        report.putResult(
                TlsAnalyzedProperty.SUPPORTED_CERT_SIGNATURE_ALGORITHMS,
                Collections.singletonList(X509SignatureAlgorithm.SHA1_WITH_RSA_ENCRYPTION));
        SignatureAndHashAlgorithmsCertificateGuidelineCheck check =
                new SignatureAndHashAlgorithmsCertificateGuidelineCheck(
                        null,
                        null,
                        Collections.singletonList(X509SignatureAlgorithm.SHA1_WITH_RSA_ENCRYPTION));
        GuidelineCheckResult result = check.evaluate(report);
        assertEquals(GuidelineAdherence.ADHERED, result.getAdherence());
    }

    @Test
    public void testNegative() {
        ServerReport report = new ServerReport("test", 443);
        report.putResult(
                TlsAnalyzedProperty.SUPPORTED_CERT_SIGNATURE_ALGORITHMS,
                Collections.singletonList(X509SignatureAlgorithm.DSA_WITH_SHA1));
        SignatureAndHashAlgorithmsCertificateGuidelineCheck check =
                new SignatureAndHashAlgorithmsCertificateGuidelineCheck(
                        null,
                        null,
                        Collections.singletonList(X509SignatureAlgorithm.SHA1_WITH_RSA_ENCRYPTION));
        GuidelineCheckResult result = check.evaluate(report);
        assertEquals(GuidelineAdherence.VIOLATED, result.getAdherence());
    }
}
