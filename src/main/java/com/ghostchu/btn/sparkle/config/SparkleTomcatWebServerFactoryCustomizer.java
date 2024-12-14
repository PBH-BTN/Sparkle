package com.ghostchu.btn.sparkle.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class SparkleTomcatWebServerFactoryCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    @Value("${server.unixsocketpath}")
    private String unixSocketPath;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        if (StringUtils.isEmpty(unixSocketPath)) {
            return;
        }
        File file = new File(unixSocketPath);
        if (file.exists()) {
            file.delete();
        }
        factory.addConnectorCustomizers(connector -> connector.setProperty("unixDomainSocketPath", unixSocketPath));
    }
}
