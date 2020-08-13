package de.rub.nds.tlsscanner.clientscanner.report;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;

import de.rub.nds.tlsscanner.clientscanner.client.ClientInfo;
import de.rub.nds.tlsscanner.clientscanner.probe.IProbe;
import de.rub.nds.tlsscanner.clientscanner.report.result.ClientProbeResult;

public class ClientReport extends Observable implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<Class<? extends IProbe>, ClientProbeResult> resultMap;
    private final ClientInfo clientInfo;

    public ClientReport(ClientInfo clientInfo) {
        this.resultMap = new HashMap<>();
        this.clientInfo = clientInfo;
    }

    public Map<Class<? extends IProbe>, ClientProbeResult> getResultMap() {
        return resultMap;
    }

    public ClientProbeResult getResult(Class<? extends IProbe> clazz) {
        return resultMap.get(clazz);
    }

    public <T extends ClientProbeResult> T getResult(Class<? extends IProbe> clazz, Class<T> expectedReturnType) {
        // convenience function
        return expectedReturnType.cast(getResult(clazz));
    }

    public ClientProbeResult putResult(Class<? extends IProbe> clazz, ClientProbeResult result) {
        return resultMap.put(clazz, result);
    }

}