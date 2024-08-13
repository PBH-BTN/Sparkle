package com.ghostchu.btn.sparkle.banhistory;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.ghostchu.btn.sparkle.banhistory.internal.BanHistory;
import com.ghostchu.btn.sparkle.exception.RequestPageSizeTooLargeException;
import com.ghostchu.btn.sparkle.torrent.internal.Torrent;
import com.ghostchu.btn.sparkle.util.compare.NumberCompareMethod;
import com.ghostchu.btn.sparkle.util.compare.StringCompareMethod;
import com.ghostchu.btn.sparkle.util.paging.SparklePage;
import com.ghostchu.btn.sparkle.wrapper.StdResp;
import jakarta.persistence.criteria.Predicate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class BanHistoryController {
    private final BanHistoryService banHistoryService;

    public BanHistoryController(BanHistoryService banHistoryService) {
        this.banHistoryService = banHistoryService;
    }

    @SaCheckLogin
    @GetMapping("/banhistory")
    public StdResp<SparklePage<?, ?>> recent(@RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) throws RequestPageSizeTooLargeException {
        if (page == null) page = 0;
        if (pageSize == null) pageSize = 100;
        if (pageSize > 3000) {
            throw new RequestPageSizeTooLargeException();
        }
        return new StdResp<>(true, null, banHistoryService.queryRecent(PageRequest.of(page, pageSize)));
    }

    @SaCheckLogin
    @PostMapping("/banhistory/query")
    public StdResp<SparklePage<?, ?>> query(@RequestBody ComplexBanQueryRequest q) throws RequestPageSizeTooLargeException {
        if (q.getPageSize() > 3000) {
            throw new RequestPageSizeTooLargeException();
        }
        Specification<BanHistory> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.isNotBlank(q.getPeerId())) {
                predicates.add(q.getPeerIdCompareMethod().criteriaBuilder(cb, root.get("peerId"), q.getPeerId()));
            }
            if (StringUtils.isNotBlank(q.getPeerClientName())) {
                predicates.add(q.getPeerIdCompareMethod().criteriaBuilder(cb, root.get("peerClientName"), q.getPeerId()));
            }
            if (StringUtils.isNotBlank(q.getTorrentIdentifier())) {
                predicates.add(q.getPeerIdCompareMethod().criteriaBuilder(cb, cb.treat(root.get("torrent"), Torrent.class).get("id"), q.getTorrentIdentifier()));
            }
            if (q.getTorrentSize() != null) {
                predicates.add(q.getTorrentSizeCompareMethod().criteriaBuilder(cb, cb.treat(root.get("torrent"), Torrent.class).get("size"), q.getTorrentSize()));
            }
            if (q.getPeerPort() != null) {
                predicates.add(q.getPeerPortCompareMethod().criteriaBuilder(cb, root.get("peerPort"), q.getPeerPort()));
            }
            if (q.getFromPeerTraffic() != null) {
                predicates.add(q.getFromPeerTrafficCompareMethod().criteriaBuilder(cb, root.get("fromPeerTraffic"), q.getFromPeerTraffic()));
            }
            if (q.getFromPeerTrafficSpeed() != null) {
                predicates.add(q.getFromPeerTrafficCompareMethod().criteriaBuilder(cb, root.get("fromPeerTrafficSpeed"), q.getFromPeerTrafficSpeed()));
            }
            if (q.getToPeerTraffic() != null) {
                predicates.add(q.getToPeerTrafficCompareMethod().criteriaBuilder(cb, root.get("toPeerTraffic"), q.getToPeerTraffic()));
            }
            if (q.getToPeerTrafficSpeed() != null) {
                predicates.add(q.getToPeerTrafficSpeedCompareMethod().criteriaBuilder(cb, root.get("toPeerTrafficSpeed"), q.getToPeerTrafficSpeed()));
            }
            if (q.getPeerProgress() != null) {
                predicates.add(q.getPeerProgressCompareMethod().criteriaBuilder(cb, root.get("peerProgress"), q.getPeerProgress()));
            }
            if (q.getDownloaderProgress() != null) {
                predicates.add(q.getDownloaderProgressCompareMethod().criteriaBuilder(cb, root.get("downloaderProgress"), q.getDownloaderProgress()));
            }
            if (q.getBtnBan() != null) {
                if (q.getBtnBan()) {
                    predicates.add(cb.isTrue(root.get("btnBan")));
                }
            }
            if (q.getModule() != null) {
                predicates.add(q.getModuleCompareMethod().criteriaBuilder(cb, root.get("module"), q.getModule()));
            }
            if (q.getRule() != null) {
                predicates.add(q.getRuleCompareMethod().criteriaBuilder(cb, root.get("rule"), q.getRule()));
            }
            if (q.getOrConnector() != null && q.getOrConnector()) {
                return cb.or(predicates.toArray(new Predicate[0]));
            } else {
                return cb.and(predicates.toArray(new Predicate[0]));
            }
        };
        Sort sort = Sort.by(q.getSortOrder(), q.getSortBy());
        return new StdResp<>(true, null, banHistoryService.complexQuery(specification, PageRequest.of(q.getPage() - 1, q.getPageSize(), sort)));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ComplexBanQueryRequest {
        private Integer page;
        private Integer pageSize;
        private Long timeFrom;
        private Long timeTo;
        private String peerId;
        private StringCompareMethod peerIdCompareMethod;
        private String peerClientName;
        private StringCompareMethod peerClientNameCompareMethod;
        private String peerIp;
        private StringCompareMethod peerIpCompareMethod;
        private String torrentIdentifier;
        private StringCompareMethod torrentIdentifierCompareMethod;
        private Long torrentSize;
        private NumberCompareMethod torrentSizeCompareMethod;
        private Integer peerPort;
        private NumberCompareMethod peerPortCompareMethod;
        private Long fromPeerTraffic;
        private NumberCompareMethod fromPeerTrafficCompareMethod;
        private Long fromPeerTrafficSpeed;
        private NumberCompareMethod fromPeerTrafficSpeedCompareMethod;
        private Long toPeerTraffic;
        private NumberCompareMethod toPeerTrafficCompareMethod;
        private Long toPeerTrafficSpeed;
        private NumberCompareMethod toPeerTrafficSpeedCompareMethod;
        private Double peerProgress;
        private NumberCompareMethod peerProgressCompareMethod;
        private Double downloaderProgress;
        private NumberCompareMethod downloaderProgressCompareMethod;
        private Boolean btnBan;
        private String module;
        private StringCompareMethod moduleCompareMethod;
        private String rule;
        private StringCompareMethod ruleCompareMethod;
        private Boolean orConnector;
        private String[] sortBy;
        private Sort.Direction sortOrder;
    }

}
