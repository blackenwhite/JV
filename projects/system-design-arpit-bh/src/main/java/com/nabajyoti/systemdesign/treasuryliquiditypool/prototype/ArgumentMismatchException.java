package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

public class ArgumentMismatchException extends RuntimeException {

    public ArgumentMismatchException(String callerId, String callerKey) {
        super("same caller key with different arguments: "
                + callerId + "/" + callerKey);
    }
}