/**
 * TLS-Server-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2022 Ruhr University Bochum, Paderborn University, Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */

package de.rub.nds.tlsscanner.serverscanner.probe;

import de.rub.nds.tlsattacker.attacks.config.BleichenbacherCommandConfig;
import de.rub.nds.tlsattacker.attacks.impl.BleichenbacherAttacker;
import de.rub.nds.tlsattacker.attacks.pkcs1.BleichenbacherWorkflowType;
import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.config.delegate.ClientDelegate;
import de.rub.nds.tlsattacker.core.config.delegate.StarttlsDelegate;
import de.rub.nds.tlsattacker.core.constants.AlgorithmResolver;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.KeyExchangeAlgorithm;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.workflow.ParallelExecutor;
import de.rub.nds.tlsscanner.serverscanner.config.ScannerConfig;
import de.rub.nds.tlsscanner.serverscanner.constants.ProbeType;
import de.rub.nds.tlsscanner.serverscanner.constants.ScannerDetail;
import de.rub.nds.tlsscanner.serverscanner.leak.info.BleichenbacherOracleTestInfo;
import de.rub.nds.tlsscanner.serverscanner.rating.TestResult;
import de.rub.nds.tlsscanner.serverscanner.rating.TestResults;
import de.rub.nds.tlsscanner.serverscanner.report.AnalyzedProperty;
import de.rub.nds.tlsscanner.serverscanner.report.SiteReport;
import de.rub.nds.tlsscanner.serverscanner.report.result.VersionSuiteListPair;
import de.rub.nds.tlsscanner.serverscanner.requirements.ProbeRequirement;
import de.rub.nds.tlsscanner.serverscanner.vectorstatistics.InformationLeakTest;
import java.util.LinkedList;
import java.util.List;

public class BleichenbacherProbe extends TlsProbe {

    private static int numberOfIterations;
    private static int numberOfAddtionalIterations;

    private List<VersionSuiteListPair> serverSupportedSuites;
    private List<InformationLeakTest<BleichenbacherOracleTestInfo>> testResultList;
    
    private TestResult vulnerable;
    
    public BleichenbacherProbe(ScannerConfig config, ParallelExecutor parallelExecutor) {
        super(parallelExecutor, ProbeType.BLEICHENBACHER, config);
        BleichenbacherProbe.numberOfIterations = scannerConfig.getScanDetail().isGreaterEqualTo(ScannerDetail.NORMAL) ? 3 : 1;
        BleichenbacherProbe.numberOfAddtionalIterations = scannerConfig.getScanDetail().isGreaterEqualTo(ScannerDetail.NORMAL) ? 7 : 9;
        super.properties.add(AnalyzedProperty.VULNERABLE_TO_BLEICHENBACHER);
    }

    @Override
    public void executeTest() {

        LOGGER.debug("Starting evaluation");
        List<BleichenbacherWorkflowType> workflowTypeList = createWorkflowTypeList();
        testResultList = new LinkedList<>();
        for (BleichenbacherWorkflowType workflowType : workflowTypeList) {
            for (VersionSuiteListPair pair : serverSupportedSuites) {
                if (!pair.getVersion().isSSL() && !pair.getVersion().isTLS13()) {
                    for (CipherSuite suite : pair.getCipherSuiteList()) {
                        if (AlgorithmResolver.getKeyExchangeAlgorithm(suite) == KeyExchangeAlgorithm.RSA
                            && CipherSuite.getImplemented().contains(suite)) {
                            BleichenbacherCommandConfig bleichenbacherConfig =
                                createBleichenbacherCommandConfig(pair.getVersion(), suite);
                            bleichenbacherConfig.setWorkflowType(workflowType);
                            testResultList.add(getBleichenbacherOracleInformationLeakTest(bleichenbacherConfig));
                        }
                    }
                }
            }
        }
        LOGGER.debug("Finished evaluation");
        if (isPotentiallyVulnerable(testResultList)
            || scannerConfig.getScanDetail().isGreaterEqualTo(ScannerDetail.NORMAL)) {
            LOGGER.debug("Starting extended evaluation");
            for (InformationLeakTest<BleichenbacherOracleTestInfo> fingerprint : testResultList) {
                if (fingerprint.isDistinctAnswers()
                    || scannerConfig.getScanDetail().isGreaterEqualTo(ScannerDetail.DETAILED)) {
                    extendFingerPrint(fingerprint, numberOfAddtionalIterations);
                }
            }
            LOGGER.debug("Finished extended evaluation");
        }
        //return new BleichenbacherResult(testResultList);
    }

    private List<BleichenbacherWorkflowType> createWorkflowTypeList() {
        List<BleichenbacherWorkflowType> vectorTypeList = new LinkedList<>();
        vectorTypeList.add(BleichenbacherWorkflowType.CKE_CCS_FIN);
        vectorTypeList.add(BleichenbacherWorkflowType.CKE);
        vectorTypeList.add(BleichenbacherWorkflowType.CKE_CCS);
        if (scannerConfig.getScanDetail() == ScannerDetail.ALL) {
            vectorTypeList.add(BleichenbacherWorkflowType.CKE_FIN);
        }
        return vectorTypeList;
    }

    private BleichenbacherCommandConfig createBleichenbacherCommandConfig(ProtocolVersion version,
        CipherSuite cipherSuite) {
        BleichenbacherCommandConfig bleichenbacherConfig =
            new BleichenbacherCommandConfig(getScannerConfig().getGeneralDelegate());
        ClientDelegate delegate = (ClientDelegate) bleichenbacherConfig.getDelegate(ClientDelegate.class);
        delegate.setHost(getScannerConfig().getClientDelegate().getHost());
        delegate.setSniHostname(getScannerConfig().getClientDelegate().getSniHostname());
        StarttlsDelegate starttlsDelegate = (StarttlsDelegate) bleichenbacherConfig.getDelegate(StarttlsDelegate.class);
        starttlsDelegate.setStarttlsType(scannerConfig.getStarttlsDelegate().getStarttlsType());
        bleichenbacherConfig.setNumberOfIterations(numberOfIterations);
        BleichenbacherCommandConfig.Type recordGeneratorType = BleichenbacherCommandConfig.Type.FAST;
        if (scannerConfig.getScanDetail().isGreaterEqualTo(ScannerDetail.ALL)) {
            recordGeneratorType = BleichenbacherCommandConfig.Type.FULL;
        }
        bleichenbacherConfig.setType(recordGeneratorType);
        bleichenbacherConfig.getCipherSuiteDelegate().setCipherSuites(cipherSuite);
        bleichenbacherConfig.getProtocolVersionDelegate().setProtocolVersion(version);
        return bleichenbacherConfig;
    }

    private InformationLeakTest<BleichenbacherOracleTestInfo>
        getBleichenbacherOracleInformationLeakTest(BleichenbacherCommandConfig bleichenbacherConfig) {
        Config config = scannerConfig.createConfig();
        BleichenbacherAttacker attacker =
            new BleichenbacherAttacker(bleichenbacherConfig, config, getParallelExecutor());
        if (scannerConfig.getScanDetail().isGreaterEqualTo(ScannerDetail.DETAILED)) {
            attacker.setAdditionalTimeout(1000);
            attacker.setIncreasingTimeout(true);
        } else {
            attacker.setAdditionalTimeout(50);
        }
        attacker.isVulnerable();
        return new InformationLeakTest<>(
            new BleichenbacherOracleTestInfo(bleichenbacherConfig.getProtocolVersionDelegate().getProtocolVersion(),
                bleichenbacherConfig.getCipherSuiteDelegate().getCipherSuites().get(0),
                bleichenbacherConfig.getWorkflowType(), bleichenbacherConfig.getType()),
            attacker.getResponseMapList());
    }

    @Override
    protected ProbeRequirement getRequirements(SiteReport report) {
        return new ProbeRequirement(report).requireProbeTypes(ProbeType.CIPHER_SUITE, ProbeType.PROTOCOL_VERSION).requireAnalyzedProperties(AnalyzedProperty.SUPPORTS_RSA);
    }

    @Override
    public void adjustConfig(SiteReport report) {
        serverSupportedSuites = report.getVersionSuitePairs();
    }

    @Override
    public TlsProbe getCouldNotExecuteResult() {
        this.vulnerable = TestResults.COULD_NOT_TEST;
        return this;
    }

    private void extendFingerPrint(InformationLeakTest<BleichenbacherOracleTestInfo> informationLeakTest,
        int numberOfAdditionalIterations) {
        BleichenbacherCommandConfig bleichenbacherConfig = createBleichenbacherCommandConfig(
            informationLeakTest.getTestInfo().getVersion(), informationLeakTest.getTestInfo().getCipherSuite());
        bleichenbacherConfig.setType(informationLeakTest.getTestInfo().getBleichenbacherType());
        bleichenbacherConfig.setWorkflowType(informationLeakTest.getTestInfo().getBleichenbacherWorkflowType());
        bleichenbacherConfig.setNumberOfIterations(numberOfAdditionalIterations);
        InformationLeakTest<BleichenbacherOracleTestInfo> intermediateResponseMap =
            getBleichenbacherOracleInformationLeakTest(bleichenbacherConfig);
        informationLeakTest.extendTestWithVectorContainers(intermediateResponseMap.getVectorContainerList());
    }

    private boolean isPotentiallyVulnerable(List<InformationLeakTest<BleichenbacherOracleTestInfo>> testResultList) {
        for (InformationLeakTest fingerprint : testResultList) {
            if (fingerprint.isDistinctAnswers()) {
                return true;
            }
        }
        return false;
    }

	@Override
	protected void mergeData(SiteReport report) {
		if (this.testResultList != null) {
            vulnerable = TestResults.FALSE;
            for (InformationLeakTest<?> informationLeakTest : this.testResultList) {
                if (informationLeakTest.isSignificantDistinctAnswers()) {
                    vulnerable = TestResults.TRUE;
                }
            }
        } else 
            vulnerable = TestResults.ERROR_DURING_TEST;
		super.setPropertyReportValue(AnalyzedProperty.VULNERABLE_TO_BLEICHENBACHER, vulnerable);
	}
}
