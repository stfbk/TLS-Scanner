/**
 * TLS-Server-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2022 Ruhr University Bochum, Paderborn University, Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */

package de.rub.nds.tlsscanner.serverscanner.guideline;

import de.rub.nds.tlsscanner.serverscanner.guideline.checks.AnalyzedPropertyGuidelineCheck;
import de.rub.nds.tlsscanner.serverscanner.rating.TestResults;
import de.rub.nds.tlsscanner.serverscanner.report.AnalyzedProperty;
import de.rub.nds.tlsscanner.serverscanner.report.SiteReport;
import org.junit.Assert;
import org.junit.Test;

public class AnalyzedPropertyGuidelineCheckTest {

    @Test
    public void testPositive() {
        SiteReport report = new SiteReport("test", 443);
        report.putResult(AnalyzedProperty.SUPPORTS_TLS13_PSK, true);

        AnalyzedPropertyGuidelineCheck check =
            new AnalyzedPropertyGuidelineCheck(null, null, AnalyzedProperty.SUPPORTS_TLS13_PSK, TestResults.TRUE);

        GuidelineCheckResult result = check.evaluate(report);

        Assert.assertEquals(TestResults.TRUE, result.getResult());
    }

    @Test
    public void testNegative() {
        SiteReport report = new SiteReport("test", 443);
        report.putResult(AnalyzedProperty.SUPPORTS_TLS13_PSK, true);

        AnalyzedPropertyGuidelineCheck check =
            new AnalyzedPropertyGuidelineCheck(null, null, AnalyzedProperty.SUPPORTS_TLS13_PSK, TestResults.FALSE);

        GuidelineCheckResult result = check.evaluate(report);

        Assert.assertEquals(TestResults.FALSE, result.getResult());
    }

    @Test
    public void testUncertain() {
        SiteReport report = new SiteReport("test", 443);

        AnalyzedPropertyGuidelineCheck check =
            new AnalyzedPropertyGuidelineCheck(null, null, AnalyzedProperty.SUPPORTS_TLS13_PSK, TestResults.FALSE);

        GuidelineCheckResult result = check.evaluate(report);

        Assert.assertEquals(TestResults.NOT_TESTED_YET, result.getResult());
    }
}
