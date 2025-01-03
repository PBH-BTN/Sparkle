package com.ghostchu.btn.sparkle.module.audit;

import com.ghostchu.btn.sparkle.module.audit.impl.Audit;
import com.ghostchu.btn.sparkle.module.audit.impl.AuditRepository;
import com.ghostchu.btn.sparkle.util.IPUtil;
import com.ghostchu.btn.sparkle.util.ServletUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

@Service
public class AuditService {
    private final AuditRepository auditRepository;
    private final Deque<Audit> auditQueue = new ConcurrentLinkedDeque<>();
    @Value("${analyse.audit.enable}")
    private boolean useAudit;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void log(HttpServletRequest req, String action, boolean success, Map<String, Object> node) {
        if (!useAudit) {
            return;
        }
        auditQueue.add(new Audit(null, OffsetDateTime.now(), IPUtil.toInet(ServletUtil.getIP(req)), action, success, getHeaders(req), node));
    }

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    @Transactional
    @Modifying
    public void updateTrackerMetrics() {
        flush();
    }

    public void flush() {
        List<Audit> toWrite = new ArrayList<>();
        while (!auditQueue.isEmpty()) {
            toWrite.add(auditQueue.poll());
        }
        auditRepository.saveAll(toWrite);
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
