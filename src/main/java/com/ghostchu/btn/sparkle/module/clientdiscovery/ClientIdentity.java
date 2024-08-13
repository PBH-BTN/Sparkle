package com.ghostchu.btn.sparkle.module.clientdiscovery;

import com.google.common.hash.Hashing;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
@NoArgsConstructor
@Data
@EqualsAndHashCode
public class ClientIdentity {
    private String peerId;
    private String clientName;
    private transient Long hash = null;

    public ClientIdentity(String peerId, String clientName) {
        this.peerId = peerId;
        this.clientName = clientName;
    }

    public long hash() {
        if(hash != null){
            return hash;
        }
        hash = Hashing.sha256().hashString(peerId + '@' + clientName, StandardCharsets.UTF_8).padToLong();
        return hash;
    }
}
