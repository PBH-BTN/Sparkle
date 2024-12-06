package com.ghostchu.btn.sparkle.util;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    public List<String> merge(List<String> sorted) {
        sorted = new ArrayList<>(sorted);
        sorted.sort(String::compareTo);
        Set<String> createdCIDR = new LinkedHashSet<>();
        IPAddress current = null;
        int counter = 0;
        sorted.removeIf(str -> str.startsWith("#"));
        for (String rule : sorted) {
            if (current == null) {
                current = toCIDR(rule);
                continue;
            }
            var ip = toIP(rule);
            if (ip == null) {
                log.warn("(Unresolved IP) {}", rule);
                continue;
            }
            if (ip.getPrefixLength() != null) {
                createdCIDR.add(rule);
                continue;
            }

            if (toCIDR(rule).equals(current)) {
                counter++;
            } else {
                counter = 0;
                current = toCIDR(rule);
                continue;
            }
            if (current.isIPv4()) {
                if (counter >= MERGE_TO_CIDR_AMOUNT_IPV4) {
                    createdCIDR.add(current.toString());
                }
            } else {
                if (counter >= MERGE_TO_CIDR_AMOUNT_IPV6) {
                    createdCIDR.add(current.toString());
                }
            }
        }
        List<String> finalSorted = sorted;
        createdCIDR.forEach(cidr -> {
            var base = toCIDR(cidr);
            finalSorted.removeIf(ip -> {
                IPAddress address = toIP(ip);
                if (address == null) {
                    System.out.println("(Unresolved data) " + ip);
                    return true;
                }
                return base.contains(address);
            });
            //sorted.removeIf(ip -> base.equals(toCIDR(ip)));
        });
        return new ArrayList<>() {{
            this.addAll(createdCIDR);
            this.addAll(finalSorted);
        }};
    }

    private IPAddress toIP(String ip) {
        try {
            return new IPAddressString(ip).getAddress();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private IPAddress toCIDR(String cidr) {
        IPAddress ipAddress = toIP(cidr);
        if (ipAddress.getPrefixLength() != null) {
            return ipAddress;
        }
        if (ipAddress.isIPv4()) {
            ipAddress = ipAddress.setPrefixLength(IPV4_PREFIX_LENGTH);
        } else {
            ipAddress = ipAddress.setPrefixLength(IPV6_PREFIX_LENGTH);
        }
        return ipAddress.toZeroHost();
    }
}
