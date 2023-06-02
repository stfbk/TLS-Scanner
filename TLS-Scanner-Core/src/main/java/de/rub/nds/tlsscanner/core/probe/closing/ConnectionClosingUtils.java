/*
 * TLS-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2023 Ruhr University Bochum, Paderborn University, and Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package de.rub.nds.tlsscanner.core.probe.closing;

import static de.rub.nds.tlsattacker.transport.socket.SocketState.CLOSED;
import static de.rub.nds.tlsattacker.transport.socket.SocketState.IO_EXCEPTION;
import static de.rub.nds.tlsattacker.transport.socket.SocketState.PEER_WRITE_CLOSED;
import static de.rub.nds.tlsattacker.transport.socket.SocketState.SOCKET_EXCEPTION;
import static de.rub.nds.tlsattacker.transport.socket.SocketState.TIMEOUT;

import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.RunningModeType;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.factory.WorkflowConfigurationFactory;
import de.rub.nds.tlsattacker.core.workflow.factory.WorkflowTraceType;
import de.rub.nds.tlsattacker.transport.socket.SocketState;
import de.rub.nds.tlsattacker.transport.tcp.TcpTransportHandler;
import java.io.IOException;

public abstract class ConnectionClosingUtils {
    public static final long NO_RESULT = -1;
    protected static final long LIMIT = 5000;

    private ConnectionClosingUtils() {}

    public static WorkflowTrace getWorkflowTrace(
            Config tlsConfig, RunningModeType runningModeType) {
        tlsConfig.setWorkflowExecutorShouldClose(false);
        WorkflowConfigurationFactory factory = new WorkflowConfigurationFactory(tlsConfig);
        return factory.createWorkflowTrace(WorkflowTraceType.DYNAMIC_HANDSHAKE, runningModeType);
    }

    public static long evaluateClosingDelta(State runningState) {
        long delta = 0;
        SocketState socketState = null;
        do {
            try {
                socketState =
                        (((TcpTransportHandler)
                                        (runningState.getTlsContext().getTransportHandler()))
                                .getSocketState());
                switch (socketState) {
                    case CLOSED:
                    case IO_EXCEPTION:
                    case PEER_WRITE_CLOSED:
                    case SOCKET_EXCEPTION:
                    case TIMEOUT:
                        closeSocket(runningState);
                        return delta;
                    default:
                }
                Thread.sleep(10);
                delta += 10;
            } catch (InterruptedException ignored) {
            }
        } while (delta < LIMIT);
        closeSocket(runningState);
        return NO_RESULT;
    }

    public static void closeSocket(State state) {
        try {
            state.getTlsContext().getTransportHandler().closeConnection();
        } catch (IOException ignored) {
        }
    }
}
