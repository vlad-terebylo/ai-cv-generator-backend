package org.example.aicvgenerator.exception;

public class ServerBusyException extends RuntimeException {

    public ServerBusyException() {
        super("The server is busy processing other CVs, please try again later");
    }
}
