/**
 * TLS-Server-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2022 Ruhr University Bochum, Paderborn University, Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */

package de.rub.nds.tlsscanner.serverscanner.probe;

import de.rub.nds.modifiablevariable.util.Modifiable;
import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.CompressionMethod;
import de.rub.nds.tlsattacker.core.constants.HandshakeMessageType;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.constants.RunningModeType;
import de.rub.nds.tlsattacker.core.protocol.ProtocolMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ApplicationMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ChangeCipherSpecMessage;
import de.rub.nds.tlsattacker.core.protocol.message.FinishedMessage;
import de.rub.nds.tlsattacker.core.record.Record;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.workflow.ParallelExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTraceUtil;
import de.rub.nds.tlsattacker.core.workflow.action.GenericReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveTillAction;
import de.rub.nds.tlsattacker.core.workflow.action.SendAction;
import de.rub.nds.tlsattacker.core.workflow.action.SendDynamicClientKeyExchangeAction;
import de.rub.nds.tlsattacker.core.workflow.factory.WorkflowConfigurationFactory;
import de.rub.nds.tlsattacker.core.workflow.factory.WorkflowTraceType;
import de.rub.nds.tlsscanner.serverscanner.config.ScannerConfig;
import de.rub.nds.tlsscanner.serverscanner.constants.ProbeType;
import de.rub.nds.tlsscanner.serverscanner.rating.TestResult;
import de.rub.nds.tlsscanner.serverscanner.rating.TestResults;
import de.rub.nds.tlsscanner.serverscanner.report.AnalyzedProperty;
import de.rub.nds.tlsscanner.serverscanner.report.SiteReport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class DtlsBugsProbe extends TlsProbe {

    private TestResult isEarlyFinished;
    private TestResult isAcceptingUnencryptedAppData;
    private TestResult isAcceptingUnencryptedFinished;
    
    public DtlsBugsProbe(ScannerConfig scannerConfig, ParallelExecutor parallelExecutor) {
        super(parallelExecutor, ProbeType.DTLS_COMMON_BUGS, scannerConfig);
        super.properties.add(AnalyzedProperty.ACCEPTS_UNENCRYPTED_FINISHED);
        super.properties.add(AnalyzedProperty.ACCEPTS_UNENCRYPTED_APP_DATA);
        super.properties.add(AnalyzedProperty.HAS_EARLY_FINISHED_BUG);
    }

    @Override
    public void executeTest() {
        try {
        	this.isEarlyFinished = isAcceptingUnencryptedFinished();
        	this.isAcceptingUnencryptedAppData = isAcceptingUnencryptedAppData();
            this.isAcceptingUnencryptedFinished = isEarlyFinished();
        } catch (Exception E) {
            LOGGER.error("Could not scan for " + getProbeName(), E);
            this.isEarlyFinished = this.isAcceptingUnencryptedAppData = this.isAcceptingUnencryptedFinished = TestResults.ERROR_DURING_TEST;          
        }
    }

    private TestResult isAcceptingUnencryptedFinished() {
        Config config = getConfig();
        WorkflowTrace trace = new WorkflowConfigurationFactory(config)
            .createWorkflowTrace(WorkflowTraceType.DYNAMIC_HELLO, RunningModeType.CLIENT);
        trace.addTlsAction(new SendDynamicClientKeyExchangeAction());
        trace.addTlsAction(new SendAction(new ChangeCipherSpecMessage(config)));
        SendAction sendAction = new SendAction(new FinishedMessage(config));
        Record record = new Record(config);
        record.setEpoch(Modifiable.explicit(0));
        sendAction.setRecords(record);
        trace.addTlsAction(sendAction);
        trace.addTlsAction(new GenericReceiveAction());
        State state = new State(config, trace);
        executeState(state);
        if (WorkflowTraceUtil.didReceiveMessage(HandshakeMessageType.FINISHED, state.getWorkflowTrace())) {
            return TestResults.TRUE;
        } else {
            return TestResults.FALSE;
        }
    }

    private TestResult isAcceptingUnencryptedAppData() {
        Config config = getConfig();
        WorkflowTrace trace = new WorkflowConfigurationFactory(config)
            .createWorkflowTrace(WorkflowTraceType.DYNAMIC_HANDSHAKE, RunningModeType.CLIENT);
        trace.addTlsAction(new SendAction(new ApplicationMessage(config)));
        trace.addTlsAction(new GenericReceiveAction());
        State state = new State(config, trace);
        executeState(state);
        ProtocolMessage receivedMessage = WorkflowTraceUtil.getLastReceivedMessage(state.getWorkflowTrace());

        trace = new WorkflowConfigurationFactory(config).createWorkflowTrace(WorkflowTraceType.DYNAMIC_HANDSHAKE,
            RunningModeType.CLIENT);
        SendAction sendAction = new SendAction(new ApplicationMessage(config));
        Record record = new Record(config);
        record.setEpoch(Modifiable.explicit(0));
        sendAction.setRecords(record);
        trace.addTlsAction(sendAction);
        trace.addTlsAction(new GenericReceiveAction());
        state = new State(config, trace);
        executeState(state);
        ProtocolMessage receivedMessageModified = WorkflowTraceUtil.getLastReceivedMessage(state.getWorkflowTrace());
        if (receivedMessage != null && receivedMessageModified != null && receivedMessage.getCompleteResultingMessage()
            .equals(receivedMessageModified.getCompleteResultingMessage())) {
            return TestResults.TRUE;
        } else {
            return TestResults.FALSE;
        }
    }

    private TestResult isEarlyFinished() {
        Config config = getConfig();
        WorkflowTrace trace = new WorkflowConfigurationFactory(config)
            .createWorkflowTrace(WorkflowTraceType.DYNAMIC_HELLO, RunningModeType.CLIENT);
        trace.addTlsAction(new SendDynamicClientKeyExchangeAction());
        trace.addTlsAction(new SendAction(new FinishedMessage(config)));
        trace.addTlsAction(new ReceiveTillAction(new FinishedMessage(config)));
        State state = new State(config, trace);
        executeState(state);
        if (WorkflowTraceUtil.didReceiveMessage(HandshakeMessageType.FINISHED, state.getWorkflowTrace())) {
            return TestResults.TRUE;
        } else {
            return TestResults.FALSE;
        }
    }

    private Config getConfig() {
        Config config = getScannerConfig().createConfig();
        config.setHighestProtocolVersion(ProtocolVersion.DTLS12);
        List<CipherSuite> ciphersuites = new LinkedList<>();
        ciphersuites.addAll(Arrays.asList(CipherSuite.values()));
        ciphersuites.remove(CipherSuite.TLS_FALLBACK_SCSV);
        ciphersuites.remove(CipherSuite.TLS_EMPTY_RENEGOTIATION_INFO_SCSV);
        config.setDefaultClientSupportedCipherSuites(ciphersuites);
        List<CompressionMethod> compressionList = new ArrayList<>(Arrays.asList(CompressionMethod.values()));
        config.setDefaultClientSupportedCompressionMethods(compressionList);
        config.setEnforceSettings(false);
        config.setQuickReceive(true);
        config.setEarlyStop(true);
        config.setStopReceivingAfterFatal(true);
        config.setStopActionsAfterFatal(true);
        config.setStopActionsAfterIOException(true);
        config.setAddECPointFormatExtension(true);
        config.setAddEllipticCurveExtension(true);
        config.setAddServerNameIndicationExtension(true);
        config.setAddSignatureAndHashAlgorithmsExtension(true);
        return config;
    }

    @Override
    public TlsProbe getCouldNotExecuteResult() {
        this.isEarlyFinished = this.isAcceptingUnencryptedAppData = this.isAcceptingUnencryptedFinished = TestResults.COULD_NOT_TEST;
        return this;
    }

    @Override
    public void adjustConfig(SiteReport report) {
    }

	@Override
	protected void mergeData(SiteReport report) {
		super.setPropertyReportValue(AnalyzedProperty.ACCEPTS_UNENCRYPTED_FINISHED, this.isAcceptingUnencryptedFinished);
	    super.setPropertyReportValue(AnalyzedProperty.ACCEPTS_UNENCRYPTED_APP_DATA, this.isAcceptingUnencryptedAppData);
	    super.setPropertyReportValue(AnalyzedProperty.HAS_EARLY_FINISHED_BUG, this.isEarlyFinished);		
	}
}
