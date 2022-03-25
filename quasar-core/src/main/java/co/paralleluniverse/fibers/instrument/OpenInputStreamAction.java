package co.paralleluniverse.fibers.instrument;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.PrivilegedAction;

final class OpenInputStreamAction implements PrivilegedAction<InputStream> {
    private final URL resource;
    private final Log log;

    OpenInputStreamAction(URL resource, Log log) {
        this.resource = resource;
        this.log = log;
    }

    @Override
    public InputStream run() {
        try {
            final URLConnection uc = resource.openConnection();
            uc.setUseCaches(false);
            return uc.getInputStream();
        } catch(IOException e) {
            final String message = "While opening " + resource;
            if (log != null) {
                log.error(message, e);
            }
            throw new UncheckedIOException(message, e);
        }
    }
}
