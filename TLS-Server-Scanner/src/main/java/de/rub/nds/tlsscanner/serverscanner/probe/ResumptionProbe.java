/**
 * TLS-Server-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2022 Ruhr University Bochum, Paderborn University, Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */

package de.rub.nds.tlsscanner.serverscanner.probe;

import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.AlertDescription;
import de.rub.nds.tlsattacker.core.constants.AlertLevel;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.ExtensionType;
import de.rub.nds.tlsattacker.core.constants.HandshakeMessageType;
import de.rub.nds.tlsattacker.core.constants.NamedGroup;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.constants.PskKeyExchangeMode;
import de.rub.nds.tlsattacker.core.constants.RunningModeType;
import de.rub.nds.tlsattacker.core.constants.SignatureAndHashAlgorithm;
import de.rub.nds.tlsattacker.core.protocol.message.AlertMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ChangeCipherSpecMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ClientHelloMessage;
import de.rub.nds.tlsattacker.core.protocol.message.EncryptedExtensionsMessage;
import de.rub.nds.tlsattacker.core.protocol.message.FinishedMessage;
import de.rub.nds.tlsattacker.core.protocol.message.HandshakeMessage;
import de.rub.nds.tlsattacker.core.protocol.message.HelloVerifyRequestMessage;
import de.rub.nds.tlsattacker.core.protocol.message.NewSessionTicketMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ServerHelloMessage;
import de.rub.nds.tlsattacker.core.protocol.message.extension.KeyShareExtensionMessage;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.workflow.ParallelExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTraceUtil;
import de.rub.nds.tlsattacker.core.workflow.action.MessageAction;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.ResetConnectionAction;
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
import de.rub.nds.tlsscanner.serverscanner.requirements.ProbeRequirement;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ResumptionProbe extends TlsProbe {

    private Set<CipherSuite> supportedSuites;
    private TestResult supportsDtlsCookieExchangeInResumption;
    private TestResult respectsPskModes;
    private TestResult supportsResumption;
    private TestResult supportsSessionTicketResumption;
    private TestResult supportsTls13SessionTicket;
    private TestResult supportsTls13PskDhe;
    private TestResult supportsTls13Psk;
    private TestResult supportsTls13_0rtt;
    private TestResult supportsDtlsCookieExchangeInSessionTicketResumption;

    public ResumptionProbe(ScannerConfig scannerConfig, ParallelExecutor parallelExecutor) {
        super(parallelExecutor, ProbeType.RESUMPTION, scannerConfig);
        super.properties.add(AnalyzedProperty.SUPPORTS_SESSION_ID_RESUMPTION);
        super.properties.add(AnalyzedProperty.SUPPORTS_SESSION_TICKET_RESUMPTION);
        super.properties.add(AnalyzedProperty.SUPPORTS_TLS13_SESSION_TICKETS);
        super.properties.add(AnalyzedProperty.SUPPORTS_TLS13_PSK_DHE);
        super.properties.add(AnalyzedProperty.SUPPORTS_TLS13_0_RTT);
        super.properties.add(AnalyzedProperty.SUPPORTS_TLS13_PSK);
        super.properties.add(AnalyzedProperty.SUPPORTS_DTLS_COOKIE_EXCHANGE_IN_SESSION_ID_RESUMPTION);
        super.properties.add(AnalyzedProperty.SUPPORTS_DTLS_COOKIE_EXCHANGE_IN_SESSION_TICKET_RESUMPTION);
        super.properties.add(AnalyzedProperty.SUPPORTS_TLS13_PSK_EXCHANGE_MODES);
    }

    @Override
    public void executeTest() {
        this.respectsPskModes = TestResults.TRUE;
        if (getScannerConfig().getDtlsDelegate().isDTLS()) {
            this.supportsDtlsCookieExchangeInResumption = getSupportsDtlsCookieExchangeInResumption();
            this.supportsDtlsCookieExchangeInSessionTicketResumption = getSupportsDtlsCookieExchangeInSessionTicketResumption();
            this.supportsTls13SessionTicket = this.supportsTls13PskDhe = this.supportsTls13Psk = this.supportsTls13_0rtt = TestResults.NOT_TESTED_YET;
        } else {
            this.supportsDtlsCookieExchangeInResumption = TestResults.NOT_TESTED_YET;
            this.supportsDtlsCookieExchangeInSessionTicketResumption = TestResults.NOT_TESTED_YET;
            this.supportsTls13SessionTicket = getIssuesSessionTicket();
            this.supportsTls13PskDhe = getSupportsTls13Psk(PskKeyExchangeMode.PSK_DHE_KE);
            this.supportsTls13Psk = getSupportsTls13Psk(PskKeyExchangeMode.PSK_KE);
            this.supportsTls13_0rtt = getSupports0rtt();
        }
        this.supportsResumption = getSupportsSessionResumption();
        this.supportsSessionTicketResumption = getSupportsSessionTicketResumption();
    }

    private TestResult getSupportsDtlsCookieExchangeInResumption() {
        try {
            Config tlsConfig = createConfig();
            WorkflowTrace trace = new WorkflowConfigurationFactory(tlsConfig)
                .createWorkflowTrace(WorkflowTraceType.DYNAMIC_HANDSHAKE, tlsConfig.getDefaultRunningMode());
            addAlertToTrace(trace);
            trace.addTlsAction(new ResetConnectionAction());
            trace.addTlsAction(new SendAction(new ClientHelloMessage(tlsConfig)));
            trace.addTlsAction(new ReceiveAction(new HelloVerifyRequestMessage(tlsConfig)));
            State state = new State(tlsConfig, trace);
            executeState(state);
            return state.getWorkflowTrace().executedAsPlanned() ? TestResults.TRUE : TestResults.FALSE;
        } catch (Exception e) {
            if (e.getCause() instanceof InterruptedException) {
                LOGGER.error("Timeout on " + getProbeName());
                throw new RuntimeException(e);
            } else {
                LOGGER.error("Could not test for support for Tls13PskDhe");
            }
            return TestResults.ERROR_DURING_TEST;
        }
    }

    private TestResult getSupportsSessionResumption() {
        try {
            Config tlsConfig = createConfig();
            tlsConfig.setWorkflowTraceType(WorkflowTraceType.FULL_RESUMPTION);
            State state = new State(tlsConfig);
            WorkflowTrace trace = new WorkflowConfigurationFactory(tlsConfig)
                .createWorkflowTrace(WorkflowTraceType.DYNAMIC_HANDSHAKE, tlsConfig.getDefaultRunningMode());
            addAlertToTrace(trace);
            trace.addTlsAction(new ResetConnectionAction());
            tlsConfig.setDtlsCookieExchange(supportsDtlsCookieExchangeInResumption == TestResults.TRUE);
            trace.addTlsActions(new WorkflowConfigurationFactory(tlsConfig)
                .createWorkflowTrace(WorkflowTraceType.RESUMPTION, tlsConfig.getDefaultRunningMode()).getTlsActions());
            state = new State(tlsConfig, trace);
            executeState(state);
            return state.getWorkflowTrace().executedAsPlanned() == true ? TestResults.TRUE : TestResults.FALSE;
        } catch (Exception e) {
            if (e.getCause() instanceof InterruptedException) {
                LOGGER.error("Timeout on " + getProbeName());
                throw new RuntimeException(e);
            } else {
                LOGGER.error("Could not test for support for SessionResumption");
            }
            return TestResults.ERROR_DURING_TEST;
        }
    }

    private TestResult getSupportsDtlsCookieExchangeInSessionTicketResumption() {
        try {
            Config tlsConfig = createConfig();
            tlsConfig.setAddSessionTicketTLSExtension(true);
            WorkflowTrace trace = new WorkflowConfigurationFactory(tlsConfig)
                .createWorkflowTrace(WorkflowTraceType.DYNAMIC_HELLO, RunningModeType.CLIENT);
            trace.addTlsAction(new SendDynamicClientKeyExchangeAction());
            trace.addTlsAction(new SendAction(new ChangeCipherSpecMessage(), new FinishedMessage()));
            trace.addTlsAction(
                new ReceiveAction(new NewSessionTicketMessage(), new ChangeCipherSpecMessage(), new FinishedMessage()));
            addAlertToTrace(trace);
            trace.addTlsAction(new ResetConnectionAction());
            trace.addTlsAction(new SendAction(new ClientHelloMessage(tlsConfig)));
            trace.addTlsAction(new ReceiveAction(new HelloVerifyRequestMessage(tlsConfig)));
            State state = new State(tlsConfig, trace);
            executeState(state);
            return state.getWorkflowTrace().executedAsPlanned() ? TestResults.TRUE : TestResults.FALSE;
        } catch (Exception e) {
            if (e.getCause() instanceof InterruptedException) {
                LOGGER.error("Timeout on " + getProbeName());
                throw new RuntimeException(e);
            } else {
                LOGGER.error("Could not test for support for dtls cookie exchange in SessionTicketResumption");
            }
            return TestResults.ERROR_DURING_TEST;
        }
    }

    private TestResult getSupportsSessionTicketResumption() {
        try {
            Config tlsConfig = createConfig();
            tlsConfig.setAddSessionTicketTLSExtension(true);
            WorkflowTrace trace = new WorkflowConfigurationFactory(tlsConfig)
                .createWorkflowTrace(WorkflowTraceType.DYNAMIC_HELLO, RunningModeType.CLIENT);
            trace.addTlsAction(new SendDynamicClientKeyExchangeAction());
            trace.addTlsAction(new SendAction(new ChangeCipherSpecMessage(), new FinishedMessage()));
            trace.addTlsAction(
                new ReceiveAction(new NewSessionTicketMessage(), new ChangeCipherSpecMessage(), new FinishedMessage()));
            addAlertToTrace(trace);
            trace.addTlsAction(new ResetConnectionAction());
            tlsConfig.setDtlsCookieExchange(supportsDtlsCookieExchangeInResumption == TestResults.TRUE);
            trace.addTlsActions(new WorkflowConfigurationFactory(tlsConfig)
                .createWorkflowTrace(WorkflowTraceType.RESUMPTION, tlsConfig.getDefaultRunningMode()).getTlsActions());
            State state = new State(tlsConfig, trace);
            executeState(state);
            return state.getWorkflowTrace().executedAsPlanned() == true ? TestResults.TRUE : TestResults.FALSE;
        } catch (Exception e) {
            if (e.getCause() instanceof InterruptedException) {
                LOGGER.error("Timeout on " + getProbeName());
                throw new RuntimeException(e);
            } else {
                LOGGER.error("Could not test for support for SessionTicketResumption");
            }
            return TestResults.ERROR_DURING_TEST;
        }
    }

    private TestResult isKeyShareExtensionNegotiated(State state) {
        List<HandshakeMessage> handshakes = WorkflowTraceUtil.getAllReceivedHandshakeMessages(state.getWorkflowTrace());
        List<ServerHelloMessage> hellos = handshakes.stream().filter(message -> message instanceof ServerHelloMessage)
            .map(message -> (ServerHelloMessage) message).collect(Collectors.toList());
        if (hellos.size() < 2) {
            return TestResults.COULD_NOT_TEST;
        }
        ServerHelloMessage second = hellos.get(1);
        second.getExtension(KeyShareExtensionMessage.class);
        return second.containsExtension(ExtensionType.KEY_SHARE) ? TestResults.TRUE : TestResults.FALSE;
    }

    private TestResult getSupportsTls13Psk(PskKeyExchangeMode exchangeMode) {
        try {
            Config tlsConfig = createTls13Config();
            List<PskKeyExchangeMode> pskKex = new LinkedList<>();
            pskKex.add(exchangeMode);
            tlsConfig.setPSKKeyExchangeModes(pskKex);
            tlsConfig.setAddPSKKeyExchangeModesExtension(true);
            tlsConfig.setAddPreSharedKeyExtension(true);
            tlsConfig.setWorkflowTraceType(WorkflowTraceType.FULL_TLS13_PSK);
            State state = new State(tlsConfig);
            executeState(state);

            MessageAction lastRcv = (MessageAction) state.getWorkflowTrace().getLastReceivingAction();
            if (lastRcv.executedAsPlanned()) {
                // Check PSK Modes
                TestResult keyShareExtensionNegotiated = isKeyShareExtensionNegotiated(state);
                TestResult keyShareRequired = TestResults.of(exchangeMode.equals(PskKeyExchangeMode.PSK_DHE_KE));
                if (!keyShareExtensionNegotiated.equals(keyShareRequired)) {
                    if (!TestResults.COULD_NOT_TEST.equals(keyShareExtensionNegotiated)) {
                        respectsPskModes = TestResults.FALSE;
                    }
                }
                return TestResults.TRUE;
            }
            return TestResults.FALSE;
        } catch (Exception e) {
            if (e.getCause() instanceof InterruptedException) {
                LOGGER.error("Timeout on " + getProbeName());
                throw new RuntimeException(e);
            } else {
                LOGGER.error("Could not test for support for Tls13Psk (" + exchangeMode + ")");
            }
            return TestResults.ERROR_DURING_TEST;
        }
    }

    private TestResult getSupports0rtt() {
        try {
            Config tlsConfig = createTls13Config();
            tlsConfig.setAddPSKKeyExchangeModesExtension(true);
            tlsConfig.setAddPreSharedKeyExtension(true);
            tlsConfig.setAddEarlyDataExtension(true);
            tlsConfig.setWorkflowTraceType(WorkflowTraceType.FULL_ZERO_RTT);
            State state = new State(tlsConfig);
            executeState(state);

            EncryptedExtensionsMessage encExt =
                state.getWorkflowTrace().getLastReceivedMessage(EncryptedExtensionsMessage.class);
            if (encExt != null && encExt.containsExtension(ExtensionType.EARLY_DATA)) {
                return TestResults.TRUE;
            }
            return TestResults.FALSE;
        } catch (Exception e) {
            if (e.getCause() instanceof InterruptedException) {
                LOGGER.error("Timeout on " + getProbeName());
                throw new RuntimeException(e);
            } else {
                LOGGER.error("Could not test for support for Tls13PskDhe");
            }
            return TestResults.ERROR_DURING_TEST;
        }
    }

    private TestResult getIssuesSessionTicket() {
        try {
            Config tlsConfig = createTls13Config();
            List<PskKeyExchangeMode> pskKex = new LinkedList<>();
            pskKex.add(PskKeyExchangeMode.PSK_DHE_KE);
            pskKex.add(PskKeyExchangeMode.PSK_KE);
            tlsConfig.setPSKKeyExchangeModes(pskKex);
            tlsConfig.setAddPSKKeyExchangeModesExtension(true);
            tlsConfig.setWorkflowTraceType(WorkflowTraceType.HANDSHAKE);
            State state = new State(tlsConfig);
            state.getWorkflowTrace().addTlsAction(new ReceiveAction(tlsConfig.getDefaultClientConnection().getAlias(),
                new NewSessionTicketMessage(false)));
            executeState(state);

            if (WorkflowTraceUtil.didReceiveMessage(HandshakeMessageType.NEW_SESSION_TICKET,
                state.getWorkflowTrace())) {
                return TestResults.TRUE;
            }
            return TestResults.FALSE;
        } catch (Exception e) {
            if (e.getCause() instanceof InterruptedException) {
                LOGGER.error("Timeout on " + getProbeName());
                throw new RuntimeException(e);
            } else {
                LOGGER.error("Could not test for support for Tls13SessionTickets");
            }
            return TestResults.ERROR_DURING_TEST;
        }
    }

    private void addAlertToTrace(WorkflowTrace trace) {
        AlertMessage alert = new AlertMessage();
        alert.setConfig(AlertLevel.WARNING, AlertDescription.CLOSE_NOTIFY);
        trace.addTlsAction(new SendAction(alert));
    }

    private Config createConfig() {
        Config tlsConfig = getScannerConfig().createConfig();
        tlsConfig.setDefaultClientSupportedCipherSuites(new ArrayList<>(supportedSuites));
        tlsConfig.setEnforceSettings(false);
        tlsConfig.setQuickReceive(true);
        tlsConfig.setEarlyStop(true);
        tlsConfig.setStopActionsAfterIOException(true);
        tlsConfig.setStopReceivingAfterFatal(true);
        tlsConfig.setStopActionsAfterFatal(true);
        tlsConfig.setAddECPointFormatExtension(true);
        tlsConfig.setAddEllipticCurveExtension(true);
        tlsConfig.setAddSignatureAndHashAlgorithmsExtension(true);
        tlsConfig.setAddRenegotiationInfoExtension(true);
        tlsConfig.setDefaultClientNamedGroups(NamedGroup.getImplemented());
        return tlsConfig;
    }

    private Config createTls13Config() {
        Config tlsConfig = getScannerConfig().createConfig();
        List<NamedGroup> tls13Groups = new LinkedList<>();
        for (NamedGroup group : NamedGroup.getImplemented()) {
            if (group.isTls13()) {
                tls13Groups.add(group);
            }
        }
        tlsConfig.setQuickReceive(true);
        tlsConfig.setDefaultClientSupportedCipherSuites(CipherSuite.getTls13CipherSuites());
        tlsConfig.setHighestProtocolVersion(ProtocolVersion.TLS13);
        tlsConfig.setSupportedVersions(ProtocolVersion.TLS13);
        tlsConfig.setEnforceSettings(false);
        tlsConfig.setEarlyStop(true);
        tlsConfig.setStopActionsAfterIOException(true);
        tlsConfig.setStopReceivingAfterFatal(true);
        tlsConfig.setStopActionsAfterFatal(true);
        tlsConfig.setDefaultClientNamedGroups(NamedGroup.getImplemented());
        tlsConfig.setAddECPointFormatExtension(false);
        tlsConfig.setAddEllipticCurveExtension(true);
        tlsConfig.setAddSignatureAndHashAlgorithmsExtension(true);
        tlsConfig.setAddSupportedVersionsExtension(true);
        tlsConfig.setAddKeyShareExtension(true);
        tlsConfig.setDefaultClientKeyShareNamedGroups(tls13Groups);
        tlsConfig.setAddCertificateStatusRequestExtension(true);
        tlsConfig.setUseFreshRandom(true);
        tlsConfig.setDefaultClientSupportedSignatureAndHashAlgorithms(
            SignatureAndHashAlgorithm.getImplementedTls13SignatureAndHashAlgorithms());
        tlsConfig.setTls13BackwardsCompatibilityMode(Boolean.TRUE);
        return tlsConfig;
    }

    @Override
    protected ProbeRequirement getRequirements(SiteReport report) {
        return new ProbeRequirement(report).requireProbeTypes(ProbeType.CIPHER_SUITE);
    }

    @Override
    public void adjustConfig(SiteReport report) {
        supportedSuites = report.getCipherSuites();
        supportedSuites.remove(CipherSuite.TLS_FALLBACK_SCSV);
        supportedSuites.remove(CipherSuite.TLS_EMPTY_RENEGOTIATION_INFO_SCSV);
    }

    @Override
    public TlsProbe getCouldNotExecuteResult() {
    	this.supportsResumption = this.supportsSessionTicketResumption = this.supportsTls13SessionTicket = this.supportsTls13PskDhe 
    			= this.supportsTls13Psk = this.supportsTls13_0rtt = this.supportsDtlsCookieExchangeInResumption 
    			= this.supportsDtlsCookieExchangeInSessionTicketResumption = this.respectsPskModes = TestResults.COULD_NOT_TEST;
        return this;
    }

	@Override
	protected void mergeData(SiteReport report) {
        super.setPropertyReportValue(AnalyzedProperty.SUPPORTS_SESSION_ID_RESUMPTION, this.supportsResumption);
        super.setPropertyReportValue(AnalyzedProperty.SUPPORTS_SESSION_TICKET_RESUMPTION, this.supportsSessionTicketResumption);
        super.setPropertyReportValue(AnalyzedProperty.SUPPORTS_TLS13_SESSION_TICKETS, this.supportsTls13SessionTicket);
        super.setPropertyReportValue(AnalyzedProperty.SUPPORTS_TLS13_PSK_DHE, this.supportsTls13PskDhe);
        super.setPropertyReportValue(AnalyzedProperty.SUPPORTS_TLS13_0_RTT, this.supportsTls13_0rtt);
        super.setPropertyReportValue(AnalyzedProperty.SUPPORTS_TLS13_PSK, this.supportsTls13Psk);
        super.setPropertyReportValue(AnalyzedProperty.SUPPORTS_DTLS_COOKIE_EXCHANGE_IN_SESSION_ID_RESUMPTION,
        		this.supportsDtlsCookieExchangeInResumption);
        super.setPropertyReportValue(AnalyzedProperty.SUPPORTS_DTLS_COOKIE_EXCHANGE_IN_SESSION_TICKET_RESUMPTION,
        		this.supportsDtlsCookieExchangeInSessionTicketResumption);
        super.setPropertyReportValue(AnalyzedProperty.SUPPORTS_TLS13_PSK_EXCHANGE_MODES, this.respectsPskModes);		
	}
}
