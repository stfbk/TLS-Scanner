/*
 * TLS-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2022 Ruhr University Bochum, Paderborn University, and Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package de.rub.nds.tlsscanner.core.exceptions;

public class OracleUnstableException extends RuntimeException {

    public OracleUnstableException() {}

    /**
     * @param message
     */
    public OracleUnstableException(String message) {
        super(message);
    }
}