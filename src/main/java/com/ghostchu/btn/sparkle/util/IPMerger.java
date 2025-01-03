package com.ghostchu.btn.sparkle.util;

import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.DualIPv4v6Tries;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class IPMerger {
    @Value("${util.ipmerger.merge-threshold.ipv4}")
    private int MERGE_TO_CIDR_AMOUNT_IPV4 = 4;
    @Value("${util.ipmerger.merge-threshold.ipv6}")
    private int MERGE_TO_CIDR_AMOUNT_IPV6 = 4;
    @Value("${util.ipmerger.prefix-length.ipv6}")
    private int IPV6_PREFIX_LENGTH = 56;
    @Value("${util.ipmerger.prefix-length.ipv4}")
    private int IPV4_PREFIX_LENGTH = 24;

    public List<String> merge(DualIPv4v6Tries tries) {
        List<String> list = new ArrayList<>();
        mergeIP(tries.getIPv4Trie().asSet(), IPV4_PREFIX_LENGTH, MERGE_TO_CIDR_AMOUNT_IPV4).forEach(ip -> list.add(ip.toString()));
        mergeIP(tries.getIPv6Trie().asSet(), IPV6_PREFIX_LENGTH, MERGE_TO_CIDR_AMOUNT_IPV6).forEach(ip -> list.add(ip.toString()));
        return list;
    }

    public <T extends IPAddress> List<IPAddress> mergeIP(Collection<T> ips, int prefixLength, int mergeThreshold) {
        // ips 去重
        ips = ips.stream().distinct().collect(Collectors.toList());
        List<IPAddress> merged = new ArrayList<>();
        // 先从 ips 中将比 IPV4_PREFIX_LENGTH 小的 IP 地址过滤出来并从集合中移除
        List<T> lessThanPrefixLength = ips.stream().filter(ip -> ip.getNetworkPrefixLength() != null && ip.getNetworkPrefixLength() < prefixLength).toList();
        ips.removeAll(lessThanPrefixLength);
        // 现在开始检查剩下的 IP 地址，如果有某个 IP 所在的 IPV4_PREFIX_LENGTH 网段中的 IP 数量大于 MERGE_TO_CIDR_AMOUNT_IPV4，则合并
        Map<IPAddress, List<T>> ipMap = ips.stream().collect(Collectors.groupingBy(ip -> ip.toPrefixBlock(prefixLength)));
        ipMap.forEach((prefix, ipList) -> {
            if (ipList.size() >= mergeThreshold) {
                merged.add(prefix);
            } else {
                merged.addAll(ipList);
            }
        });
        // 如果结果集中包含可被 lessThanPrefixLength 中的 CIDR contains 的地址，这些就从最终结果中排除
        merged.removeIf(ip -> lessThanPrefixLength.stream().anyMatch(less -> less.contains(ip)));
        merged.addAll(lessThanPrefixLength);
        return merged;
    }
}
