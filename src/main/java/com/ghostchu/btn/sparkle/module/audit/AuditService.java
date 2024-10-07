package com.ghostchu.btn.sparkle.module.audit;

import com.ghostchu.btn.sparkle.module.audit.impl.Audit;
import com.ghostchu.btn.sparkle.module.audit.impl.AuditRepository;
import com.ghostchu.btn.sparkle.util.IPUtil;
import com.ghostchu.btn.sparkle.util.ServletUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AuditService {
    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void log(HttpServletRequest req, String action, boolean success, Map<String, Object> node) {
        var audit = new Audit(null, OffsetDateTime.now(), IPUtil.toInet(ServletUtil.getIP(req)), action, success, getHeaders(req), node);
        auditRepository.save(audit);
    }

    public Map<String, List<String>> getHeaders(HttpServletRequest req) {
        Map<String, List<String>> map = new LinkedMultiValueMap<>();
        req.getHeaderNames().asIterator().forEachRemaining(key -> {
            if (key.equalsIgnoreCase("X-BTN-AppSecret")) return;
            if (key.equalsIgnoreCase("BTN-AppSecret")) return;
            if (key.equalsIgnoreCase("Authorization")) return;
            List<String> list = new ArrayList<>();
            req.getHeaders(key).asIterator().forEachRemaining(list::add);
            map.put(key, list);
        });
        return map;
    }

}
