package com.ghostchu.btn.sparkle.module.torrent;

import com.ghostchu.btn.sparkle.controller.SparkleController;
import com.ghostchu.btn.sparkle.wrapper.StdResp;
import com.google.common.hash.Hashing;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@RestController
@RequestMapping("/api/torrent")
public class TorrentController extends SparkleController {

    @GetMapping("/hash")
    public StdResp<HashInfoHashResponse> hashInfoHash(@RequestParam("hash") @NotEmpty @Valid String hash) {
        String torrentInfoHandled = hash.toLowerCase(Locale.ROOT); // 转小写处理
        String salt = Hashing.crc32().hashString(torrentInfoHandled, StandardCharsets.UTF_8).toString(); // 使用 crc32 计算 info_hash 的哈希作为盐
        String torrentIdentifier = Hashing.sha256().hashString(torrentInfoHandled + salt, StandardCharsets.UTF_8).toString(); // 在 info_hash 的明文后面追加盐后，计算 SHA256 的哈希值，结果应转全小写
        return new StdResp<>(true, null, new HashInfoHashResponse(torrentIdentifier));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HashInfoHashResponse {
        private String torrentIdentifier;
    }
}
