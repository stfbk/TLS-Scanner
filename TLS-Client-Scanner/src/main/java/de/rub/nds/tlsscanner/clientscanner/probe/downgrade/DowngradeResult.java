package de.rub.nds.tlsscanner.clientscanner.probe.downgrade;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.protocol.message.ClientHelloMessage;
import de.rub.nds.tlsscanner.clientscanner.probe.IProbe;
import de.rub.nds.tlsscanner.clientscanner.report.ClientReport;
import de.rub.nds.tlsscanner.clientscanner.report.result.ClientProbeResult;

@XmlAccessorType(XmlAccessType.FIELD)
public class DowngradeResult extends ClientProbeResult {
    private final transient Class<? extends IProbe> clazz;
    private final boolean protocolVersionChanged;

    public DowngradeResult(Class<? extends IProbe> clazz, ClientHelloMessage chlo1, ClientHelloMessage chlo2) {
        this.clazz = clazz;

        protocolVersionChanged = !getProtocolVersion(chlo1).equals(getProtocolVersion(chlo2));

        // TODO evaluate supported versions
        // TODO evaluate supported ciphersuites
    }

    protected ProtocolVersion getProtocolVersion(ClientHelloMessage chlo) {
        return ProtocolVersion.getProtocolVersion(chlo.getProtocolVersion().getValue());
    }

    @Override
    public void merge(ClientReport report) {
        report.putResult(clazz, this);
    }

}
