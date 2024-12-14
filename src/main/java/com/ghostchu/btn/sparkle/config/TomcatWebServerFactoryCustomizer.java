package com.ghostchu.btn.sparkle.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatWebServerFactoryCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    @Value("${server.unixsocketpath}")
    private String unixSocketPath;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        if (StringUtils.isEmpty(unixSocketPath)) {
            return;
        }
        factory.addConnectorCustomizers(connector -> connector.setProperty("unixDomainSocketPath", unixSocketPath));
    }
}
