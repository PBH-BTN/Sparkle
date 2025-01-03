package com.ghostchu.btn.sparkle.util;

import inet.ipaddr.format.util.DualIPv4v6Tries;
import inet.ipaddr.ipv4.IPv4Address;
import inet.ipaddr.ipv6.IPv6Address;
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
        mergeIPv4(tries.getIPv4Trie().asSet()).forEach(ip -> list.add(ip.toString()));
        mergeIPv6(tries.getIPv6Trie().asSet()).forEach(ip -> list.add(ip.toString()));
        return list;
    }

    public List<IPv6Address> mergeIPv6(Collection<IPv6Address> ips) {
        // ips 去重
        ips = ips.stream().distinct().collect(Collectors.toList());
        List<IPv6Address> merged = new ArrayList<>();
        // 先从 ips 中将比 IPV4_PREFIX_LENGTH 小的 IP 地址过滤出来并从集合中移除
        List<IPv6Address> lessThanPrefixLength = ips.stream().filter(ip -> ip.getNetworkPrefixLength() != null && ip.getNetworkPrefixLength() < IPV4_PREFIX_LENGTH).toList();
        ips.removeAll(lessThanPrefixLength);
        // 现在开始检查剩下的 IP 地址，如果有某个 IP 所在的 IPV4_PREFIX_LENGTH 网段中的 IP 数量大于 MERGE_TO_CIDR_AMOUNT_IPV4，则合并
        Map<IPv6Address, List<IPv6Address>> ipMap = ips.stream().collect(Collectors.groupingBy(ip -> ip.toPrefixBlock(IPV6_PREFIX_LENGTH)));
        ipMap.forEach((prefix, ipList) -> {
            if (ipList.size() >= MERGE_TO_CIDR_AMOUNT_IPV6) {
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

    public List<IPv4Address> mergeIPv4(Collection<IPv4Address> ips) {
        // ips 去重
        ips = ips.stream().distinct().collect(Collectors.toList());
        List<IPv4Address> merged = new ArrayList<>();
        // 先从 ips 中将比 IPV4_PREFIX_LENGTH 小的 IP 地址过滤出来并从集合中移除
        List<IPv4Address> lessThanPrefixLength = ips.stream().filter(ip -> ip.getNetworkPrefixLength() != null && ip.getNetworkPrefixLength() < IPV4_PREFIX_LENGTH).toList();
        ips.removeAll(lessThanPrefixLength);
        // 现在开始检查剩下的 IP 地址，如果有某个 IP 所在的 IPV4_PREFIX_LENGTH 网段中的 IP 数量大于 MERGE_TO_CIDR_AMOUNT_IPV4，则合并
        Map<IPv4Address, List<IPv4Address>> ipMap = ips.stream().collect(Collectors.groupingBy(ip -> ip.toPrefixBlock(IPV4_PREFIX_LENGTH)));
        ipMap.forEach((prefix, ipList) -> {
            if (ipList.size() >= MERGE_TO_CIDR_AMOUNT_IPV4) {
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
