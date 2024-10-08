package com.ghostchu.btn.sparkle.module.clientdiscovery;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.ghostchu.btn.sparkle.controller.SparkleController;
import com.ghostchu.btn.sparkle.exception.RequestPageSizeTooLargeException;
import com.ghostchu.btn.sparkle.module.clientdiscovery.internal.ClientDiscovery;
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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@RestController
@SaCheckLogin
@RequestMapping("/api")
public class ClientDiscoveryController extends SparkleController {
    private final ClientDiscoveryService clientDiscoveryService;

    public ClientDiscoveryController(ClientDiscoveryService clientDiscoveryService) {
        this.clientDiscoveryService = clientDiscoveryService;
    }

    @GetMapping("/clientdiscovery")
    public StdResp<SparklePage<?,?>> recent(@RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) throws RequestPageSizeTooLargeException {
        var paging = paging(page,pageSize);
        return new StdResp<>(true, null, clientDiscoveryService.queryRecent(PageRequest.of(paging.page(), paging.pageSize())));
    }

    @PostMapping("/clientdiscovery/query")
    public StdResp<SparklePage<?,?>> complexQuery(@RequestBody ComplexDiscoverQueryRequest q) throws RequestPageSizeTooLargeException {
        var paging = paging(q.getPage(), q.getPageSize());
        Specification<ClientDiscovery> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if(q.getFoundAtTimeFrom() != null){
                predicates.add(cb.greaterThanOrEqualTo(root.get("foundAt"),  new Timestamp(q.getFoundAtTimeFrom())));
            }
            if(q.getFoundAtTimeTo() != null){
                predicates.add(cb.lessThanOrEqualTo(root.get("foundAt"),  new Timestamp(q.getFoundAtTimeTo())));
            }
            if(q.getLastSeenAtTimeFrom() != null){
                predicates.add(cb.greaterThanOrEqualTo(root.get("lastSeenAt"), new Timestamp(q.getLastSeenAtTimeFrom())));
            }
            if(q.getLastSeenAtTimeTo() != null){
                predicates.add(cb.lessThanOrEqualTo(root.get("laseSeenAt"),  new Timestamp(q.getLastSeenAtTimeTo())));
            }
            if (StringUtils.isNotBlank(q.getPeerId())) {
                predicates.add(q.getPeerIdCompareMethod().criteriaBuilder(cb, root.get("peerId"), q.getPeerId()));
            }
            if (StringUtils.isNotBlank(q.getPeerClientName())) {
                predicates.add(q.getPeerIdCompareMethod().criteriaBuilder(cb, root.get("peerClientName"), q.getPeerId()));
            }
            if (q.getOrConnector() != null && q.getOrConnector()) {
                return cb.or(predicates.toArray(new Predicate[0]));
            } else {
                return cb.and(predicates.toArray(new Predicate[0]));
            }
        };
        Sort sort = Sort.unsorted();
        if(q.getSortOrder() != null && q.getSortBy() != null){
            sort = Sort.by(q.getSortOrder(), q.getSortBy());
        }
        return new StdResp<>(true, null, clientDiscoveryService.query(specification, PageRequest.of(paging.page(), paging.pageSize(), sort)));

    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ComplexDiscoverQueryRequest {
        private Integer page;
        private Integer pageSize;
        private Long foundAtTimeFrom;
        private Long foundAtTimeTo;
        private Long lastSeenAtTimeFrom;
        private Long lastSeenAtTimeTo;
        private String peerId;
        private StringCompareMethod peerIdCompareMethod;
        private String peerClientName;
        private Boolean orConnector;
        private String[] sortBy;
        private Sort.Direction sortOrder;
    }
}
