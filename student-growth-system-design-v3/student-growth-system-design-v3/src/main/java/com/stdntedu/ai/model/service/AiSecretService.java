package com.stdntedu.ai.model.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.ai.model.entity.AiSecretEntity;
import com.stdntedu.ai.model.mapper.AiSecretMapper;
import com.stdntedu.ai.model.security.AiSecretCryptoService;
import com.stdntedu.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AiSecretService {
    private final AiSecretMapper secrets;
    private final AiSecretCryptoService crypto;

    public AiSecretService(AiSecretMapper secrets, AiSecretCryptoService crypto) {
        this.secrets = secrets;
        this.crypto = crypto;
    }

    public String create(String apiKey) {
        String ref = "ais_" + UUID.randomUUID().toString().replace("-", "");
        AiSecretEntity entity = encrypted(ref, apiKey);
        secrets.insert(entity);
        return ref;
    }

    public void replace(String secretRef, String apiKey) {
        AiSecretEntity existing = require(secretRef);
        AiSecretEntity replacement = encrypted(secretRef, apiKey);
        replacement.setId(existing.getId());
        replacement.setCreateTime(existing.getCreateTime());
        secrets.updateById(replacement);
    }

    public void delete(String secretRef) {
        if (secretRef != null) secrets.deleteById(require(secretRef).getId());
    }

    public <T> T withDecryptedSecret(String secretRef, Function<char[], T> action) {
        AiSecretEntity entity = require(secretRef);
        if (!AiSecretCryptoService.ALGORITHM.equals(entity.getAlgorithm())
                || !Integer.valueOf(AiSecretCryptoService.KEY_VERSION).equals(entity.getKeyVersion())) {
            throw integrityError();
        }
        char[] plaintext = crypto.decrypt(entity.getSecretRef(), entity.getEncryptedValue(), entity.getNonce());
        try {
            return action.apply(plaintext);
        } finally {
            Arrays.fill(plaintext, '\0');
        }
    }

    public SecretView view(String secretRef) {
        if (secretRef == null) return new SecretView(false, null);
        AiSecretEntity entity = require(secretRef);
        return new SecretView(true, "****" + entity.getMaskSuffix());
    }

    public Map<String, SecretView> views(Collection<String> secretRefs) {
        var refs = secretRefs.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (refs.isEmpty()) return Map.of();
        Map<String, AiSecretEntity> found = secrets.selectList(Wrappers.<AiSecretEntity>lambdaQuery()
                .in(AiSecretEntity::getSecretRef, refs)).stream()
                .collect(Collectors.toMap(AiSecretEntity::getSecretRef, entity -> entity));
        for (String ref : refs) if (!found.containsKey(ref)) throw integrityError();
        return found.values().stream().collect(Collectors.toMap(AiSecretEntity::getSecretRef,
                entity -> new SecretView(true, "****" + entity.getMaskSuffix())));
    }

    private AiSecretEntity encrypted(String ref, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("BUSINESS_RULE_VIOLATION", "API key must not be blank",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        char[] chars = apiKey.toCharArray();
        try {
            AiSecretCryptoService.EncryptedSecret encrypted = crypto.encrypt(ref, chars);
            AiSecretEntity entity = new AiSecretEntity();
            entity.setSecretRef(ref);
            entity.setEncryptedValue(encrypted.encryptedValue());
            entity.setNonce(encrypted.nonce());
            entity.setAlgorithm(encrypted.algorithm());
            entity.setKeyVersion(encrypted.keyVersion());
            entity.setMaskSuffix(apiKey.substring(Math.max(0, apiKey.length() - 4)));
            return entity;
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    private AiSecretEntity require(String secretRef) {
        AiSecretEntity entity = secrets.selectOne(Wrappers.<AiSecretEntity>lambdaQuery()
                .eq(AiSecretEntity::getSecretRef, secretRef));
        if (entity == null) throw integrityError();
        return entity;
    }

    private BusinessException integrityError() {
        return new BusinessException("DATA_INTEGRITY_ERROR", "AI model secret reference is invalid",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public record SecretView(boolean configured, String masked) { }
}
