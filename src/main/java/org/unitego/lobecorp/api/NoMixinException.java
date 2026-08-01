package org.unitego.lobecorp.api;

public class NoMixinException extends IllegalStateException {
    public NoMixinException() {
        super("A mixin should have implemented this method!");
    }
}
