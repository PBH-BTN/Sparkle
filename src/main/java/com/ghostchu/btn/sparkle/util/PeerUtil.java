package com.ghostchu.btn.sparkle.util;

import org.apache.commons.lang3.StringUtils;

public class PeerUtil {
    /**
     * 处理PeerId，截断为前 8 个字符，如果为空(空串/null)，返回 N/A
     * @param peerId 原始 PeerId
     * @return 截断后的 PeerId 或 N/A（如果为空）
     */
    public static String cutPeerId(String peerId){
       return StringUtils.left(StringUtils.defaultIfEmpty(peerId, "N/A"), 8);
    }

    /**
     * 处理ClientName，如果为空(空串/null)，返回 N/A
     * @param clientName ClientName
     * @return ClientName 或 N/A（如果为空）
     */
    public static String cutClientName(String clientName){
        return StringUtils.defaultIfEmpty(clientName, "N/A");
    }
}
